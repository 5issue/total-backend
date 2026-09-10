package com.kurly.auth.application;

import com.kurly.auth.application.dto.SocialLoginResult;
import com.kurly.auth.application.dto.TokenPair;
import com.kurly.auth.application.port.UserProfileClient;
import com.kurly.auth.domain.entity.AuthUser;
import com.kurly.auth.domain.enums.AuthProvider;
import com.kurly.auth.domain.repository.AuthUserRepository;
import com.kurly.auth.infrastructure.oauth.OAuthClient;
import com.kurly.auth.infrastructure.oauth.OAuthProviderProperties;
import com.kurly.auth.infrastructure.oauth.OAuthTransaction;
import com.kurly.auth.infrastructure.security.jwt.IssuedToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SocialAuthServiceUnitTest {

    private static final String REDIRECT = "http://localhost:8081/api/v1/auth/oauth/kakao/callback";
    private static final OAuthTransaction TRANSACTION =
            new OAuthTransaction("state-v", "verifier-v", REDIRECT);

    @Mock OAuthClient oAuthClient;
    @Mock AuthUserRepository authUserRepository;
    @Mock UserProfileClient userProfileClient;
    @Mock AuthTokenService authTokenService;

    SocialAuthService socialAuthService;

    @BeforeEach
    void setUp() {
        OAuthProviderProperties properties = new OAuthProviderProperties(
                List.of(REDIRECT),
                Map.of(AuthProvider.KAKAO, new OAuthProviderProperties.Provider(
                        "cid", "secret", "https://a", "https://t", "https://u", null, "id",
                        true, OAuthProviderProperties.TokenRequestMethod.POST)));
        socialAuthService = new SocialAuthService(
                oAuthClient, properties, authUserRepository, userProfileClient, authTokenService);
    }

    private static TokenPair tokenPair() {
        return new TokenPair(
                new IssuedToken("access", Instant.now().plusSeconds(1800), Duration.ofMinutes(30), "a"),
                new IssuedToken("refresh", Instant.now().plusSeconds(1209600), Duration.ofDays(14), "r"));
    }

    @Nested
    @DisplayName("인가 요청 생성")
    class AuthorizationRequestTest {

        @Test
        void 허용된_redirectUri면_인가_URL과_컨텍스트를_만든다() {
            given(oAuthClient.buildAuthorizationUri(any(), any(), any())).willReturn("https://provider/login");

            SocialAuthService.AuthorizationRequest request =
                    socialAuthService.createAuthorizationRequest(AuthProvider.KAKAO, REDIRECT);

            assertThat(request.loginUrl()).isEqualTo("https://provider/login");
            assertThat(request.transaction().redirectUri()).isEqualTo(REDIRECT);
            assertThat(request.transaction().state()).isNotBlank();
            assertThat(request.transaction().codeVerifier()).isNotBlank();
        }

        @Test
        void 매_요청마다_state와_verifier가_새로_생성된다() {
            given(oAuthClient.buildAuthorizationUri(any(), any(), any())).willReturn("https://provider/login");

            OAuthTransaction first = socialAuthService
                    .createAuthorizationRequest(AuthProvider.KAKAO, REDIRECT).transaction();
            OAuthTransaction second = socialAuthService
                    .createAuthorizationRequest(AuthProvider.KAKAO, REDIRECT).transaction();

            assertThat(first.state()).isNotEqualTo(second.state());
            assertThat(first.codeVerifier()).isNotEqualTo(second.codeVerifier());
        }
    }

    @Nested
    @DisplayName("콜백 처리")
    class CallbackTest {

        @Test
        void 기존_회원은_회원도메인_호출_없이_토큰을_받는다() {
            AuthUser existing = AuthUser.builder()
                    .provider(AuthProvider.KAKAO).providerId("pid").userId(50001L).build();
            given(oAuthClient.exchangeCodeForAccessToken(any(), any(), any())).willReturn("provider-token");
            given(oAuthClient.fetchProviderId(AuthProvider.KAKAO, "provider-token")).willReturn("pid");
            given(authUserRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "pid"))
                    .willReturn(Optional.of(existing));
            given(authTokenService.issueUserTokens(existing)).willReturn(tokenPair());

            SocialLoginResult result = socialAuthService.handleCallback(
                    AuthProvider.KAKAO, "code", "state-v", TRANSACTION);

            assertThat(result.userId()).isEqualTo(50001L);
            assertThat(result.newUser()).isFalse();
            // 로그인 경로는 user-service에 의존하지 않아야 한다.
            verify(userProfileClient, never()).syncProfile(any(), any());
        }

        @Test
        void 신규_회원은_회원도메인과_동기화한_뒤_저장된다() {
            given(oAuthClient.exchangeCodeForAccessToken(any(), any(), any())).willReturn("provider-token");
            given(oAuthClient.fetchProviderId(AuthProvider.KAKAO, "provider-token")).willReturn("new-pid");
            given(authUserRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "new-pid"))
                    .willReturn(Optional.empty());
            given(userProfileClient.syncProfile(AuthProvider.KAKAO, "new-pid"))
                    .willReturn(new UserProfileClient.SyncedProfile(70001L, true));
            given(authUserRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
            given(authTokenService.issueUserTokens(any())).willReturn(tokenPair());

            SocialLoginResult result = socialAuthService.handleCallback(
                    AuthProvider.KAKAO, "code", "state-v", TRANSACTION);

            assertThat(result.userId()).isEqualTo(70001L);
            assertThat(result.newUser()).isTrue();
            verify(userProfileClient).syncProfile(AuthProvider.KAKAO, "new-pid");
        }
    }
}
