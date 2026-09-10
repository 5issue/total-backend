package com.kurly.auth.infrastructure.oauth;

import com.kurly.auth.domain.enums.AuthProvider;
import com.kurly.auth.exception.AuthErrorCode;
import com.kurly.auth.support.StubHttpServer;
import com.kurly.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthClientUnitExceptionTest {

    private static final OAuthTransaction TRANSACTION =
            new OAuthTransaction("state-value", "verifier-value", "http://localhost/callback");

    private StubHttpServer stub;

    @AfterEach
    void tearDown() {
        if (stub != null) {
            stub.close();
        }
    }

    private OAuthClient client(String tokenUri, String userInfoUri, String userIdPath) {
        OAuthProviderProperties.Provider provider = new OAuthProviderProperties.Provider(
                "client-id", "secret", "https://a", tokenUri, userInfoUri, null, userIdPath,
                true, OAuthProviderProperties.TokenRequestMethod.POST);
        return new OAuthClient(new OAuthProviderProperties(List.of(), Map.of(AuthProvider.KAKAO, provider)));
    }

    @Nested
    @DisplayName("설정 누락")
    class ConfigurationTest {

        @Test
        void 등록되지_않은_제공자는_기동_오류로_드러난다() {
            OAuthClient client = new OAuthClient(new OAuthProviderProperties(List.of(), Map.of()));

            assertThatThrownBy(() -> client.buildAuthorizationUri(AuthProvider.NAVER, TRANSACTION, "c"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("소셜 제공자 설정이 없습니다");
        }
    }

    @Nested
    @DisplayName("토큰 교환 실패")
    class TokenExchangeFailureTest {

        @Test
        void 제공자가_오류를_반환하면_인가코드_오류로_변환된다() {
            stub = new StubHttpServer().stub("/token", 400, "{\"error\":\"invalid_grant\"}");
            OAuthClient client = client(stub.url("/token"), "https://u", "id");

            assertThatThrownBy(() ->
                    client.exchangeCodeForAccessToken(AuthProvider.KAKAO, "bad-code", TRANSACTION))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(AuthErrorCode.INVALID_AUTH_CODE));
        }

        @Test
        void access_token이_없는_응답도_인가코드_오류로_처리된다() {
            stub = new StubHttpServer().stub("/token", 200, "{\"token_type\":\"bearer\"}");
            OAuthClient client = client(stub.url("/token"), "https://u", "id");

            assertThatThrownBy(() ->
                    client.exchangeCodeForAccessToken(AuthProvider.KAKAO, "code", TRANSACTION))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("사용자 정보 조회 실패")
    class UserInfoFailureTest {

        @Test
        void 조회_자체가_실패하면_인가코드_오류로_변환된다() {
            stub = new StubHttpServer().stub("/userinfo", 401, "{\"msg\":\"unauthorized\"}");
            OAuthClient client = client("https://t", stub.url("/userinfo"), "id");

            assertThatThrownBy(() -> client.fetchProviderId(AuthProvider.KAKAO, "bad-token"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        void 지정한_경로에_식별자가_없으면_거부된다() {
            stub = new StubHttpServer().stub("/userinfo", 200, "{\"nickname\":\"tester\"}");
            OAuthClient client = client("https://t", stub.url("/userinfo"), "id");

            assertThatThrownBy(() -> client.fetchProviderId(AuthProvider.KAKAO, "token"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        void 중첩_경로가_중간에서_끊겨도_거부된다() {
            stub = new StubHttpServer().stub("/userinfo", 200, "{\"response\":\"플랫한 값\"}");
            OAuthClient client = client("https://t", stub.url("/userinfo"), "response.id");

            assertThatThrownBy(() -> client.fetchProviderId(AuthProvider.KAKAO, "token"))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
