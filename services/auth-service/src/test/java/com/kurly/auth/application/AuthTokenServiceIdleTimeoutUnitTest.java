package com.kurly.auth.application;

import com.kurly.auth.domain.entity.AdminRefreshToken;
import com.kurly.auth.domain.entity.AuthAdmin;
import com.kurly.auth.domain.entity.AuthUser;
import com.kurly.auth.domain.entity.UserRefreshToken;
import com.kurly.auth.domain.enums.AuthProvider;
import com.kurly.auth.domain.repository.AdminRefreshTokenRepository;
import com.kurly.auth.domain.repository.UserRefreshTokenRepository;
import com.kurly.auth.infrastructure.messaging.SessionActivityLag;
import com.kurly.auth.infrastructure.security.IdleTimeoutProperties;
import com.kurly.auth.infrastructure.security.RefreshTokenHasher;
import com.kurly.auth.infrastructure.security.jwt.IssuedToken;
import com.kurly.auth.infrastructure.security.jwt.JwtTokenProvider;
import com.kurly.auth.infrastructure.security.jwt.TokenClaims;
import com.kurly.common.exception.UnauthorizedException;
import com.kurly.common.security.Role;
import com.kurly.common.security.TokenType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

/**
 * 유휴 세션 자동 차단(인증인가_설계서 1.6).
 *
 * <p>판정은 <b>갱신 요청 시점에만</b> 일어난다. 배치가 세션을 훑지 않으므로 한도를 넘긴 세션도
 * 다음 갱신 시도 전까지는 DB에 남아 있다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("유휴 세션 자동 차단")
class AuthTokenServiceIdleTimeoutUnitTest {

    private static final String RAW = "raw-refresh-token";
    private static final String HASH = "hashed-refresh-token";
    private static final Duration USER_LIMIT = Duration.ofMinutes(30);
    private static final Duration ADMIN_LIMIT = Duration.ofMinutes(15);

    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock RefreshTokenHasher refreshTokenHasher;
    @Mock UserRefreshTokenRepository userRefreshTokenRepository;
    @Mock AdminRefreshTokenRepository adminRefreshTokenRepository;

    @BeforeEach
    void stubCommon() {
        lenient().when(refreshTokenHasher.hash(RAW)).thenReturn(HASH);
        lenient().when(jwtTokenProvider.parse(RAW, TokenType.REFRESH))
                .thenReturn(new TokenClaims(1L, Role.USER, TokenType.REFRESH, "jti", Instant.now()));
    }

    private AuthTokenService service(boolean enabled) {
        return service(enabled, new SessionActivityLag());
    }

    private AuthTokenService service(boolean enabled, SessionActivityLag lag) {
        return new AuthTokenService(jwtTokenProvider, refreshTokenHasher,
                userRefreshTokenRepository, adminRefreshTokenRepository,
                new IdleTimeoutProperties(enabled, USER_LIMIT, ADMIN_LIMIT), lag);
    }

    /** 컨슈머가 {@code behind}만큼 밀려 있고 큐에 적체가 남은 상태. */
    private SessionActivityLag laggingBy(Duration behind) {
        SessionActivityLag lag = new SessionActivityLag();
        lag.onFlush(Instant.now().minus(behind), 1L);
        return lag;
    }

    private AuthUser user() {
        return AuthUser.builder().provider(AuthProvider.KAKAO).providerId("pid").userId(50001L).build();
    }

    private UserRefreshToken tokenLastUsed(AuthUser owner, LocalDateTime lastUsedAt) {
        return UserRefreshToken.builder()
                .token(HASH)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .lastUsedAt(lastUsedAt)
                .authUser(owner)
                .build();
    }

    /**
     * 유휴 판정만 없었다면 갱신이 성공했을 상태를 만든다.
     *
     * <p><b>lenient가 필요하다.</b> 차단 테스트에서는 이 스텁에 도달하지 않는데, 스텁을 빼면
     * {@code revokeIfActive}가 기본값 0을 돌려주어 <b>동시 사용 감지 분기</b>로 빠진다.
     * 그러면 유휴 판정을 지워도 같은 예외가 나서 테스트가 회귀를 잡지 못한다.
     */
    private void stubRotationWouldSucceed() {
        lenient().when(userRefreshTokenRepository.revokeIfActive(HASH)).thenReturn(1);
        lenient().when(jwtTokenProvider.issueAccessToken(any(), any()))
                .thenReturn(new IssuedToken("access", Instant.now().plusSeconds(1800), Duration.ofMinutes(30), "a"));
        lenient().when(jwtTokenProvider.issueRefreshToken(any(), any()))
                .thenReturn(new IssuedToken("refresh", Instant.now().plusSeconds(60), Duration.ofDays(14), "r"));
    }


    private AuthAdmin admin() {
        return AuthAdmin.builder().loginId("ops").password("h").adminId(9999L).build();
    }

    private AdminRefreshToken adminTokenLastUsed(AuthAdmin owner, LocalDateTime lastUsedAt) {
        return AdminRefreshToken.builder()
                .token(HASH)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .lastUsedAt(lastUsedAt)
                .authAdmin(owner)
                .build();
    }

    private void givenAdminRole() {
        lenient().when(jwtTokenProvider.parse(RAW, TokenType.REFRESH))
                .thenReturn(new TokenClaims(9999L, Role.ADMIN, TokenType.REFRESH, "jti", Instant.now()));
    }

    @Test
    void 한도를_넘긴_세션은_전체_무효화되고_거부된다() {
        AuthUser owner = user();
        UserRefreshToken idle = tokenLastUsed(owner, LocalDateTime.now().minusMinutes(31));
        UserRefreshToken other = tokenLastUsed(owner, LocalDateTime.now().minusMinutes(31));
        given(userRefreshTokenRepository.findByToken(HASH)).willReturn(Optional.of(idle));
        given(userRefreshTokenRepository.findAllByAuthUserId(any())).willReturn(List.of(idle, other));
        stubRotationWouldSucceed();

        assertThatThrownBy(() -> service(true).refresh(RAW))
                .isInstanceOf(UnauthorizedException.class);

        // 유휴 차단은 해당 토큰만이 아니라 세션 전체를 끊는다.
        assertThat(idle.isRevoked()).isTrue();
        assertThat(other.isRevoked()).isTrue();
    }

    @Test
    void 한도_이내면_정상_갱신된다() {
        given(userRefreshTokenRepository.findByToken(HASH))
                .willReturn(Optional.of(tokenLastUsed(user(), LocalDateTime.now().minusMinutes(29))));
        stubRotationWouldSucceed();

        assertThatCode(() -> service(true).refresh(RAW)).doesNotThrowAnyException();
    }

    @Test
    void 비활성이면_한도를_넘겨도_통과한다() {
        given(userRefreshTokenRepository.findByToken(HASH))
                .willReturn(Optional.of(tokenLastUsed(user(), LocalDateTime.now().minusDays(3))));
        stubRotationWouldSucceed();

        assertThatCode(() -> service(false).refresh(RAW)).doesNotThrowAnyException();
    }

    @Test
    void 활동_기록이_없으면_차단하지_않는다() {
        // 컬럼 추가 이전에 발급된 세션. 기록이 없다고 끊으면 배포 직후 전원이 로그아웃된다.
        given(userRefreshTokenRepository.findByToken(HASH))
                .willReturn(Optional.of(tokenLastUsed(user(), null)));
        stubRotationWouldSucceed();

        assertThatCode(() -> service(true).refresh(RAW)).doesNotThrowAnyException();
    }

    @Test
    void 갱신에_성공하면_활동_시각이_기록된다() {
        given(userRefreshTokenRepository.findByToken(HASH))
                .willReturn(Optional.of(tokenLastUsed(user(), LocalDateTime.now().minusMinutes(1))));
        stubRotationWouldSucceed();

        service(true).refresh(RAW);

        // 갱신 자체가 활동이다. 이벤트 경로가 죽어도 이 기록은 남는다.
        org.mockito.ArgumentCaptor<UserRefreshToken> saved =
                org.mockito.ArgumentCaptor.forClass(UserRefreshToken.class);
        org.mockito.Mockito.verify(userRefreshTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getLastUsedAt()).isNotNull();
    }

    @Test
    void touch는_더_과거의_기록으로_덮어쓰지_않는다() {
        LocalDateTime recent = LocalDateTime.now().minusMinutes(1);
        UserRefreshToken token = tokenLastUsed(user(), recent);

        token.touch(LocalDateTime.now().minusMinutes(10));

        assertThat(token.getLastUsedAt()).isEqualTo(recent);
    }

    @Test
    void 반영_지연만큼_빼고_판정한다() {
        // 기록은 31분 전이지만 컨슈머가 5분 밀려 있다 → 실제 유휴는 26분일 수 있다.
        given(userRefreshTokenRepository.findByToken(HASH))
                .willReturn(Optional.of(tokenLastUsed(user(), LocalDateTime.now().minusMinutes(31))));
        stubRotationWouldSucceed();

        assertThatCode(() -> service(true, laggingBy(Duration.ofMinutes(5))).refresh(RAW))
                .doesNotThrowAnyException();
    }

    @Test
    void 지연이_한도_이상이면_판정을_우회한다() {
        // 판정 근거가 무의미한 상태다. 끊지 않고 통과시킨다(통제 해제는 로그로 남는다).
        given(userRefreshTokenRepository.findByToken(HASH))
                .willReturn(Optional.of(tokenLastUsed(user(), LocalDateTime.now().minusDays(1))));
        stubRotationWouldSucceed();

        assertThatCode(() -> service(true, laggingBy(USER_LIMIT.plusMinutes(1))).refresh(RAW))
                .doesNotThrowAnyException();
    }

    @Test
    void 관리자도_한도를_넘기면_세션이_폐기된다() {
        // 관리자 한도는 15분으로 회원보다 짧다.
        givenAdminRole();
        AuthAdmin owner = admin();
        AdminRefreshToken idle = adminTokenLastUsed(owner, LocalDateTime.now().minusMinutes(16));
        given(adminRefreshTokenRepository.findByToken(HASH)).willReturn(Optional.of(idle));
        given(adminRefreshTokenRepository.findAllByAuthAdminId(any())).willReturn(List.of(idle));
        lenient().when(adminRefreshTokenRepository.revokeIfActive(HASH)).thenReturn(1);

        assertThatThrownBy(() -> service(true).refresh(RAW))
                .isInstanceOf(UnauthorizedException.class);

        assertThat(idle.isRevoked()).isTrue();
    }

    @Test
    void 관리자도_한도_이내면_정상_갱신된다() {
        givenAdminRole();
        given(adminRefreshTokenRepository.findByToken(HASH))
                .willReturn(Optional.of(adminTokenLastUsed(admin(), LocalDateTime.now().minusMinutes(14))));
        lenient().when(adminRefreshTokenRepository.revokeIfActive(HASH)).thenReturn(1);
        lenient().when(jwtTokenProvider.issueAccessToken(any(), any()))
                .thenReturn(new IssuedToken("access", Instant.now().plusSeconds(1800), Duration.ofMinutes(30), "a"));
        lenient().when(jwtTokenProvider.issueRefreshToken(any(), any()))
                .thenReturn(new IssuedToken("refresh", Instant.now().plusSeconds(60), Duration.ofDays(14), "r"));

        assertThatCode(() -> service(true).refresh(RAW)).doesNotThrowAnyException();
    }

    @Test
    void 관리자_토큰도_활동_시각을_기록하고_덮어쓰기를_막는다() {
        LocalDateTime recent = LocalDateTime.now().minusMinutes(1);
        AdminRefreshToken token = adminTokenLastUsed(admin(), recent);

        token.touch(LocalDateTime.now().minusMinutes(10));

        assertThat(token.getLastUsedAt()).isEqualTo(recent);
        assertThat(token.lastActivityAt()).isEqualTo(recent);
    }
}
