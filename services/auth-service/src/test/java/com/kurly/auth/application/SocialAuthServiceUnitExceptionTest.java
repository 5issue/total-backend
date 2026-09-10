package com.kurly.auth.application;

import com.kurly.auth.application.port.UserProfileClient;
import com.kurly.auth.domain.enums.AuthProvider;
import com.kurly.auth.domain.entity.AuthUser;
import com.kurly.auth.domain.enums.UserStatus;
import com.kurly.auth.domain.repository.AuthUserRepository;
import com.kurly.auth.infrastructure.oauth.OAuthClient;
import com.kurly.auth.infrastructure.oauth.OAuthProviderProperties;
import com.kurly.auth.infrastructure.oauth.OAuthTransaction;
import com.kurly.common.exception.BusinessException;
import com.kurly.common.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SocialAuthServiceUnitExceptionTest {

    private static final String ALLOWED_REDIRECT = "http://localhost:8081/api/v1/auth/oauth/kakao/callback";

    @Mock OAuthClient oAuthClient;
    @Mock AuthUserRepository authUserRepository;
    @Mock UserProfileClient userProfileClient;
    @Mock AuthTokenService authTokenService;

    SocialAuthService socialAuthService;

    @BeforeEach
    void setUp() {
        OAuthProviderProperties properties = new OAuthProviderProperties(
                List.of(ALLOWED_REDIRECT),
                Map.of(AuthProvider.KAKAO, new OAuthProviderProperties.Provider(
                        "client", "secret", "https://authorize", "https://token", "https://userinfo",
                        null, "id", true, OAuthProviderProperties.TokenRequestMethod.POST)));
        socialAuthService = new SocialAuthService(
                oAuthClient, properties, authUserRepository, userProfileClient, authTokenService);
    }

    @Nested
    @DisplayName("redirect_uri 검증")
    class RedirectUriTest {

        @Test
        void 허용_목록에_없는_redirectUri는_거부된다() {
            assertThatThrownBy(() -> socialAuthService.createAuthorizationRequest(
                    AuthProvider.KAKAO, "https://attacker.example.com/steal"))
                    .isInstanceOf(BusinessException.class);

            // 인가 URL 자체를 만들지 않아야 한다. 만들면 공격자 주소가 담긴 URL이 나간다.
            verify(oAuthClient, never()).buildAuthorizationUri(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("state 검증")
    class StateTest {

        @Test
        void 콜백의_state가_쿠키와_다르면_거부된다() {
            OAuthTransaction stored = new OAuthTransaction("stored-state", "verifier", ALLOWED_REDIRECT);

            assertThatThrownBy(() -> socialAuthService.handleCallback(
                    AuthProvider.KAKAO, "auth-code", "forged-state", stored))
                    .isInstanceOf(UnauthorizedException.class);

            // state가 어긋나면 인가 코드를 교환하지 않는다.
            verify(oAuthClient, never()).exchangeCodeForAccessToken(any(), any(), any());
            verify(userProfileClient, never()).syncProfile(any(), any());
        }
    }

    @Nested
    @DisplayName("회원 상태")
    class UserStatusTest {

        @Test
        void 탈퇴한_회원은_로그인할_수_없다() {
            AuthUser withdrawn = AuthUser.builder()
                    .provider(AuthProvider.KAKAO).providerId("pid").userId(1L).build();
            ReflectionTestUtils.setField(withdrawn, "status", UserStatus.WITHDRAWN);
            OAuthTransaction stored = new OAuthTransaction("s", "v", ALLOWED_REDIRECT);
            given(oAuthClient.exchangeCodeForAccessToken(any(), any(), any())).willReturn("t");
            given(oAuthClient.fetchProviderId(any(), any())).willReturn("pid");
            given(authUserRepository.findByProviderAndProviderId(any(), any()))
                    .willReturn(Optional.of(withdrawn));

            assertThatThrownBy(() -> socialAuthService.handleCallback(
                    AuthProvider.KAKAO, "code", "s", stored))
                    .isInstanceOf(UnauthorizedException.class);

            verify(authTokenService, never()).issueUserTokens(any());
        }
    }
}
