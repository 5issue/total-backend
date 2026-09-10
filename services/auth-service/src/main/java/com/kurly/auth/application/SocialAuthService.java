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
import com.kurly.auth.infrastructure.oauth.PkceChallenge;
import com.kurly.common.exception.BusinessException;
import com.kurly.common.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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
    public AuthorizationRequest createAuthorizationRequest(AuthProvider provider, String redirectUri) {
        // 검증하지 않으면 공격자가 자기 서버를 redirect_uri로 지정해 인가 코드를 가로챌 수 있다(BE-16).
        if (!oAuthProviderProperties.isAllowedRedirectUri(redirectUri)) {
            log.warn("허용되지 않은 redirect_uri 요청: {}", redirectUri);
            throw new BusinessException(AuthErrorCode.BAD_REQUEST, "허용되지 않은 redirectUri 입니다.");
        }

        PkceChallenge pkce = PkceChallenge.generate();
        OAuthTransaction transaction =
                new OAuthTransaction(PkceChallenge.generateState(), pkce.verifier(), redirectUri);
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
        String providerId = oAuthClient.fetchProviderId(provider, providerAccessToken);

        AuthUser authUser = authUserRepository.findByProviderAndProviderId(provider, providerId).orElse(null);
        boolean newUser = false;

        if (authUser == null) {
            // 회원 도메인이 id를 소유하므로 여기서 동기화하고 참조값을 받아온다. 멱등이라 재시도해도 안전하다.
            UserProfileClient.SyncedProfile profile = userProfileClient.syncProfile(provider, providerId);
            authUser = authUserRepository.save(AuthUser.builder()
                    .provider(provider)
                    .providerId(providerId)
                    .userId(profile.userId())
                    .build());
            newUser = profile.newUser();
            log.info("소셜 회원가입 완료: provider={}, userId={}", provider, profile.userId());
        }

        if (authUser.getStatus() != UserStatus.ACTIVE) {
            log.info("로그인 불가 상태의 회원: userId={}, status={}", authUser.getUserId(), authUser.getStatus());
            throw new UnauthorizedException(INVALID_AUTH_CODE_MESSAGE);
        }

        return new SocialLoginResult(
                authTokenService.issueUserTokens(authUser), authUser.getUserId(), newUser);
    }

    public record AuthorizationRequest(String loginUrl, OAuthTransaction transaction) {
    }
}
