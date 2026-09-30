package com.kurly.auth.application;

import com.kurly.auth.application.dto.SocialLoginResult;
import com.kurly.auth.application.port.UserProfileClient;
import com.kurly.auth.domain.entity.AuthUser;
import com.kurly.auth.domain.enums.AuthProvider;
import com.kurly.auth.domain.enums.UserStatus;
import com.kurly.auth.domain.repository.AuthUserRepository;
import com.kurly.auth.exception.AuthErrorCode;
import com.kurly.auth.infrastructure.oauth.OAuthClient;
import com.kurly.auth.infrastructure.oauth.OAuthProviderProperties;
import com.kurly.auth.infrastructure.oauth.OAuthTransaction;
import com.kurly.auth.infrastructure.oauth.ReturnToPath;
import com.kurly.auth.infrastructure.oauth.PkceChallenge;
import com.kurly.common.exception.BusinessException;
import com.kurly.common.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 로그인. 인가 URL 발급과 콜백 처리를 담당한다(인증인가_설계서 1.2).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialAuthService {

    public static final String UNSUPPORTED_PROVIDER_MESSAGE = "지원하지 않는 서비스 제공자입니다.";
    public static final String INVALID_AUTH_CODE_MESSAGE = "유효하지 않은 인가코드입니다.";

    private final OAuthClient oAuthClient;
    private final OAuthProviderProperties oAuthProviderProperties;
    private final AuthUserRepository authUserRepository;
    private final UserProfileClient userProfileClient;
    private final AuthTokenService authTokenService;

    /** 인가 URL과, 콜백까지 이어져야 할 컨텍스트를 함께 만든다. */
    public AuthorizationRequest createAuthorizationRequest(AuthProvider provider, String redirectUri,
                                                          String returnTo) {
        // 검증하지 않으면 공격자가 자기 서버를 redirect_uri로 지정해 인가 코드를 가로챌 수 있다(BE-01).
        if (!oAuthProviderProperties.isAllowedRedirectUri(redirectUri)) {
            log.warn("허용되지 않은 redirect_uri 요청: {}", redirectUri);
            throw new BusinessException(AuthErrorCode.BAD_REQUEST, "허용되지 않은 redirectUri 입니다.");
        }

        PkceChallenge pkce = PkceChallenge.generate();
        OAuthTransaction transaction = new OAuthTransaction(
                PkceChallenge.generateState(), pkce.verifier(), redirectUri,
                ReturnToPath.sanitize(returnTo).orElse(null));
        String loginUrl = oAuthClient.buildAuthorizationUri(provider, transaction, pkce.challenge());
        return new AuthorizationRequest(loginUrl, transaction);
    }

    /**
     * 콜백 처리. 상태 검증 → 코드 교환 → 회원 확인/생성 → 토큰 발급 순으로 진행한다.
     *
     * <p>회원 도메인 호출은 <b>신규 가입 시에만</b> 일어난다. 기존 회원 로그인은 auth-service 안에서 끝난다.
     */
    @Transactional
    public SocialLoginResult handleCallback(AuthProvider provider, String code,
                                            String state, OAuthTransaction transaction) {
        // 쿠키에 담아둔 state와 대조해 위조된 콜백을 걸러낸다.
        if (!transaction.state().equals(state)) {
            log.warn("state 불일치로 콜백 거부: provider={}", provider);
            throw new UnauthorizedException(INVALID_AUTH_CODE_MESSAGE);
        }

        String providerAccessToken = oAuthClient.exchangeCodeForAccessToken(provider, code, transaction);
        OAuthClient.SocialUser socialUser = oAuthClient.fetchUser(provider, providerAccessToken);
        String providerId = socialUser.providerId();

        AuthUser authUser = authUserRepository.findByProviderAndProviderId(provider, providerId).orElse(null);

        if (authUser == null) {
            // 회원 도메인이 id를 소유하므로 여기서 동기화하고 참조값을 받아온다. 멱등이라 재시도해도 안전하다.
            UserProfileClient.SyncedProfile profile =
                    userProfileClient.syncProfile(provider, providerId, socialUser.name());
            authUser = authUserRepository.save(AuthUser.builder()
                    .provider(provider)
                    .providerId(providerId)
                    .userId(profile.userId())
                    .build());
            log.info("소셜 회원가입 완료: provider={}, userId={}", provider, profile.userId());
        } else {
            backfillName(provider, providerId, socialUser.name());
        }

        if (authUser.getStatus() != UserStatus.ACTIVE) {
            log.info("로그인 불가 상태의 회원: userId={}, status={}", authUser.getUserId(), authUser.getStatus());
            throw new UnauthorizedException(INVALID_AUTH_CODE_MESSAGE);
        }

        return new SocialLoginResult(
                authTokenService.issueUserTokens(authUser), authUser.getUserId());
    }

    /**
     * 기존 회원의 비어 있는 이름을 채운다. <b>실패해도 로그인을 막지 않는다.</b>
     *
     * <p>신규 가입과 달리 여기서는 user-service 응답이 토큰 발급에 필요하지 않다. 보정에 실패했다고
     * 로그인을 깨뜨리면, 이름 한 칸 때문에 user-service 장애가 로그인 장애로 번진다.
     *
     * <p>제공자가 이름을 주지 않았으면 호출조차 하지 않는다. 덮어쓸 값이 없는데 로그인마다
     * 서비스 간 호출을 늘릴 이유가 없다.
     */
    private void backfillName(AuthProvider provider, String providerId, String name) {
        if (!StringUtils.hasText(name)) {
            return;
        }
        try {
            userProfileClient.syncProfile(provider, providerId, name);
        } catch (Exception e) {
            log.warn("회원 이름 보정 실패. 로그인은 계속한다: provider={}", provider, e);
        }
    }

    public record AuthorizationRequest(String loginUrl, OAuthTransaction transaction) {
    }
}
