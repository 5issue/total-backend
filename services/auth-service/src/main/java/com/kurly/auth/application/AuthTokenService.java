package com.kurly.auth.application;

import com.kurly.auth.application.dto.TokenPair;
import com.kurly.auth.domain.entity.AdminRefreshToken;
import com.kurly.auth.domain.entity.AuthAdmin;
import com.kurly.auth.domain.entity.AuthUser;
import com.kurly.auth.domain.entity.UserRefreshToken;
import com.kurly.auth.domain.enums.AdminStatus;
import com.kurly.common.security.Role;
import com.kurly.auth.domain.enums.UserStatus;
import com.kurly.auth.domain.repository.AdminRefreshTokenRepository;
import com.kurly.auth.domain.repository.UserRefreshTokenRepository;
import com.kurly.auth.infrastructure.security.RefreshTokenHasher;
import com.kurly.auth.infrastructure.security.jwt.IssuedToken;
import com.kurly.auth.infrastructure.security.jwt.JwtTokenProvider;
import com.kurly.auth.infrastructure.security.jwt.TokenClaims;
import com.kurly.common.security.TokenType;
import com.kurly.common.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 토큰 재발급. 인증인가_설계서 1.5의 rotation·재사용 감지 규칙을 구현한다.
 *
 * <p>실패 사유는 응답에 노출하지 않고 모두 {@link UnauthorizedException}으로 수렴시킨다
 * (시큐어코딩가이드 BE-17). 구분이 필요한 상황은 로그로만 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthTokenService {

    /**
     * API 명세가 정한 실패 응답 메시지. 실패 사유(미존재·폐기·만료·위변조)를 구분하지 않고 하나로 수렴시킨다
     * (시큐어코딩가이드 BE-17).
     */
    public static final String INVALID_REFRESH_TOKEN_MESSAGE =
            "유효하지 않거나 만료된 리프레시 토큰입니다. 다시 로그인해주세요.";

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenHasher refreshTokenHasher;
    private final UserRefreshTokenRepository userRefreshTokenRepository;
    private final AdminRefreshTokenRepository adminRefreshTokenRepository;

    /**
     * 재사용이 감지되면 세션 전체를 무효화한 뒤 요청을 거부한다. 이때 무효화 결과는 커밋되어야 하므로
     * {@code noRollbackFor}로 롤백 대상에서 제외한다. 이것이 없으면 무효화가 함께 롤백되어
     * 탈취된 토큰이 계속 살아 있게 된다.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public TokenPair refresh(String refreshToken) {
        TokenClaims claims = jwtTokenProvider.parse(refreshToken, TokenType.REFRESH);
        String tokenHash = refreshTokenHasher.hash(refreshToken);

        return switch (claims.role()) {
            case USER -> refreshUserToken(tokenHash);
            case ADMIN -> refreshAdminToken(tokenHash);
        };
    }

    private TokenPair refreshUserToken(String tokenHash) {
        UserRefreshToken stored = userRefreshTokenRepository.findByToken(tokenHash)
                .orElseThrow(AuthTokenService::invalidRefreshToken);
        AuthUser user = stored.getAuthUser();

        if (stored.isRevoked()) {
            log.warn("폐기된 refresh token 재사용 감지: authUserId={}", user.getId());
            revokeAllUserSessions(user.getId());
            throw invalidRefreshToken();
        }
        verifyNotExpired(stored.getExpiresAt());
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.info("갱신 불가 상태의 회원: authUserId={}, status={}", user.getId(), user.getStatus());
            throw invalidRefreshToken();
        }

        stored.revoke();

        return issueUserTokens(user);
    }

    /**
     * 소셜 로그인·재발급이 공통으로 쓰는 회원 토큰 발급.
     * 토큰의 sub에는 인증 도메인 id가 아니라 회원 도메인 참조값을 담는다.
     */
    @Transactional
    public TokenPair issueUserTokens(AuthUser user) {
        IssuedToken access = jwtTokenProvider.issueAccessToken(user.getUserId(), Role.USER);
        IssuedToken refresh = jwtTokenProvider.issueRefreshToken(user.getUserId(), Role.USER);
        userRefreshTokenRepository.save(UserRefreshToken.builder()
                .token(refreshTokenHasher.hash(refresh.token()))
                .expiresAt(toLocalDateTime(refresh.expiresAt()))
                .authUser(user)
                .build());
        return new TokenPair(access, refresh);
    }

    private TokenPair refreshAdminToken(String tokenHash) {
        AdminRefreshToken stored = adminRefreshTokenRepository.findByToken(tokenHash)
                .orElseThrow(AuthTokenService::invalidRefreshToken);
        AuthAdmin admin = stored.getAuthAdmin();

        if (stored.isRevoked()) {
            log.warn("폐기된 refresh token 재사용 감지: authAdminId={}", admin.getId());
            revokeAllAdminSessions(admin.getId());
            throw invalidRefreshToken();
        }
        verifyNotExpired(stored.getExpiresAt());
        if (admin.getStatus() != AdminStatus.ACTIVE) {
            log.info("갱신 불가 상태의 관리자: authAdminId={}, status={}", admin.getId(), admin.getStatus());
            throw invalidRefreshToken();
        }

        stored.revoke();

        return issueAdminTokens(admin);
    }

    /**
     * 관리자 로그인·재발급이 공통으로 쓰는 토큰 발급.
     * refresh token은 원본이 아닌 해시로 저장한다(인증인가_설계서 1.5).
     */
    @Transactional
    public TokenPair issueAdminTokens(AuthAdmin admin) {
        IssuedToken access = jwtTokenProvider.issueAccessToken(admin.getAdminId(), Role.ADMIN);
        IssuedToken refresh = jwtTokenProvider.issueRefreshToken(admin.getAdminId(), Role.ADMIN);
        adminRefreshTokenRepository.save(AdminRefreshToken.builder()
                .token(refreshTokenHasher.hash(refresh.token()))
                .expiresAt(toLocalDateTime(refresh.expiresAt()))
                .authAdmin(admin)
                .build());
        return new TokenPair(access, refresh);
    }

    /**
     * 명시적 로그아웃. 해당 계정의 refresh token 세션을 제거한다(인증인가_설계서 1.6).
     *
     * <p><b>현재 세션만 골라 끊을 수 없다.</b> refresh token 쿠키는 {@code Path=/api/v1/auth/refresh}로
     * 좁혀져 있어 로그아웃 요청에는 실리지 않고, access token과 refresh token을 잇는 식별자도 없다.
     * 따라서 access token으로 확인한 계정의 세션 전체를 제거한다.
     *
     * <p>access token은 stateless라 만료(최대 30분)까지 유효하다. 즉시 차단이 필요하면
     * 별도의 무효화 수단이 필요하다.
     */
    @Transactional
    public void logout(Long userId, Role role) {
        switch (role) {
            case USER -> userRefreshTokenRepository.deleteAllByAuthUserUserId(userId);
            case ADMIN -> adminRefreshTokenRepository.deleteAllByAuthAdminAdminId(userId);
        }
        log.info("로그아웃 처리: userId={}, role={}", userId, role);
    }

    private void revokeAllUserSessions(Long authUserId) {
        List<UserRefreshToken> sessions = userRefreshTokenRepository.findAllByAuthUserId(authUserId);
        sessions.stream().filter(token -> !token.isRevoked()).forEach(UserRefreshToken::revoke);
    }

    private void revokeAllAdminSessions(Long authAdminId) {
        List<AdminRefreshToken> sessions = adminRefreshTokenRepository.findAllByAuthAdminId(authAdminId);
        sessions.stream().filter(token -> !token.isRevoked()).forEach(AdminRefreshToken::revoke);
    }

    /**
     * JWT의 exp는 이미 검증되었지만, 서버 측에서 강제 만료·정리한 레코드를 걸러내기 위해 저장소 기준으로도 확인한다.
     */
    private void verifyNotExpired(LocalDateTime expiresAt) {
        if (expiresAt.isBefore(LocalDateTime.now())) {
            throw invalidRefreshToken();
        }
    }

    private static UnauthorizedException invalidRefreshToken() {
        return new UnauthorizedException(INVALID_REFRESH_TOKEN_MESSAGE);
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
