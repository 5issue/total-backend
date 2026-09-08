package com.kurly.auth.infrastructure.oauth;

import com.kurly.auth.domain.enums.AuthProvider;
import com.kurly.auth.support.StubHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthClientUnitTest {

    private static final String REDIRECT_URI = "http://localhost:8081/api/v1/auth/oauth/kakao/callback";
    private static final OAuthTransaction TRANSACTION =
            new OAuthTransaction("state-value", "verifier-value", REDIRECT_URI);

    private StubHttpServer stub;

    @AfterEach
    void tearDown() {
        if (stub != null) {
            stub.close();
        }
    }

    private OAuthProviderProperties properties(OAuthProviderProperties.Provider provider) {
        return new OAuthProviderProperties(List.of(REDIRECT_URI), Map.of(AuthProvider.KAKAO, provider));
    }

    private OAuthProviderProperties.Provider provider(String tokenUri, String userInfoUri, String scope,
                                                      boolean pkce,
                                                      OAuthProviderProperties.TokenRequestMethod method) {
        return new OAuthProviderProperties.Provider(
                "client-id", "client-secret", "https://provider.example/authorize",
                tokenUri, userInfoUri, scope, "id", pkce, method);
    }

    private static Map<String, String> queryOf(String url) {
        String query = URI.create(url).getRawQuery();
        return Arrays.stream(query.split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(
                        parts -> URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        parts -> parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : ""));
    }

    @Nested
    @DisplayName("인가 URL 생성")
    class AuthorizationUriTest {

        @Test
        void 필수_파라미터가_모두_실린다() {
            OAuthClient client = new OAuthClient(properties(provider(
                    "https://t", "https://u", null, true, OAuthProviderProperties.TokenRequestMethod.POST)));

            String url = client.buildAuthorizationUri(AuthProvider.KAKAO, TRANSACTION, "challenge-value");

            Map<String, String> query = queryOf(url);
            assertThat(query).containsEntry("response_type", "code")
                    .containsEntry("client_id", "client-id")
                    .containsEntry("redirect_uri", REDIRECT_URI)
                    .containsEntry("state", "state-value");
        }

        @Test
        void redirect_uri가_퍼센트_인코딩된다() {
            OAuthClient client = new OAuthClient(properties(provider(
                    "https://t", "https://u", null, true, OAuthProviderProperties.TokenRequestMethod.POST)));

            String url = client.buildAuthorizationUri(AuthProvider.KAKAO, TRANSACTION, "challenge-value");

            // 인코딩하지 않으면 '://'가 그대로 실려 제공자가 값을 잘못 해석할 수 있다.
            assertThat(url).contains("redirect_uri=http%3A%2F%2Flocalhost%3A8081%2F");
            assertThat(url).doesNotContain("redirect_uri=http://");
        }

        @Test
        void PKCE가_켜지면_challenge와_method가_실린다() {
            OAuthClient client = new OAuthClient(properties(provider(
                    "https://t", "https://u", null, true, OAuthProviderProperties.TokenRequestMethod.POST)));

            Map<String, String> query = queryOf(
                    client.buildAuthorizationUri(AuthProvider.KAKAO, TRANSACTION, "challenge-value"));

            assertThat(query).containsEntry("code_challenge", "challenge-value")
                    .containsEntry("code_challenge_method", "S256");
        }

        @Test
        void PKCE가_꺼지면_challenge가_실리지_않는다() {
            OAuthClient client = new OAuthClient(properties(provider(
                    "https://t", "https://u", null, false, OAuthProviderProperties.TokenRequestMethod.POST)));

            Map<String, String> query = queryOf(
                    client.buildAuthorizationUri(AuthProvider.KAKAO, TRANSACTION, "challenge-value"));

            assertThat(query).doesNotContainKey("code_challenge")
                    .doesNotContainKey("code_challenge_method");
        }

        @Test
        void scope가_설정된_경우에만_실린다() {
            OAuthClient withScope = new OAuthClient(properties(provider(
                    "https://t", "https://u", "openid", true, OAuthProviderProperties.TokenRequestMethod.POST)));
            OAuthClient withoutScope = new OAuthClient(properties(provider(
                    "https://t", "https://u", null, true, OAuthProviderProperties.TokenRequestMethod.POST)));

            assertThat(queryOf(withScope.buildAuthorizationUri(AuthProvider.KAKAO, TRANSACTION, "c")))
                    .containsEntry("scope", "openid");
            assertThat(queryOf(withoutScope.buildAuthorizationUri(AuthProvider.KAKAO, TRANSACTION, "c")))
                    .doesNotContainKey("scope");
        }
    }

    @Nested
    @DisplayName("인가 코드 교환")
    class TokenExchangeTest {

        @Test
        void POST_방식은_폼_본문으로_보낸다() {
            stub = new StubHttpServer().stub("/token", 200, "{\"access_token\":\"provider-token\"}");
            OAuthClient client = new OAuthClient(properties(provider(
                    stub.url("/token"), "https://u", null, true,
                    OAuthProviderProperties.TokenRequestMethod.POST)));

            String token = client.exchangeCodeForAccessToken(AuthProvider.KAKAO, "auth-code", TRANSACTION);

            assertThat(token).isEqualTo("provider-token");
            StubHttpServer.Recorded request = stub.lastReceived();
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.contentType()).contains("application/x-www-form-urlencoded");
            assertThat(request.body()).contains("grant_type=authorization_code")
                    .contains("code=auth-code")
                    .contains("code_verifier=verifier-value")
                    .contains("state=state-value");
        }

        @Test
        void GET_방식은_쿼리스트링으로_보낸다() {
            stub = new StubHttpServer().stub("/token", 200, "{\"access_token\":\"provider-token\"}");
            OAuthClient client = new OAuthClient(properties(provider(
                    stub.url("/token"), "https://u", null, true,
                    OAuthProviderProperties.TokenRequestMethod.GET)));

            String token = client.exchangeCodeForAccessToken(AuthProvider.KAKAO, "auth-code", TRANSACTION);

            assertThat(token).isEqualTo("provider-token");
            StubHttpServer.Recorded request = stub.lastReceived();
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.uri()).contains("grant_type=authorization_code");
        }

        @Test
        void PKCE가_꺼지면_code_verifier를_보내지_않는다() {
            stub = new StubHttpServer().stub("/token", 200, "{\"access_token\":\"provider-token\"}");
            OAuthClient client = new OAuthClient(properties(provider(
                    stub.url("/token"), "https://u", null, false,
                    OAuthProviderProperties.TokenRequestMethod.POST)));

            client.exchangeCodeForAccessToken(AuthProvider.KAKAO, "auth-code", TRANSACTION);

            assertThat(stub.lastReceived().body()).doesNotContain("code_verifier");
        }
    }

    @Nested
    @DisplayName("사용자 식별자 조회")
    class UserInfoTest {

        @Test
        void Bearer_헤더로_조회하고_최상위_경로에서_식별자를_꺼낸다() {
            stub = new StubHttpServer().stub("/userinfo", 200, "{\"id\":123456789}");
            OAuthClient client = new OAuthClient(properties(provider(
                    "https://t", stub.url("/userinfo"), null, true,
                    OAuthProviderProperties.TokenRequestMethod.POST)));

            String providerId = client.fetchProviderId(AuthProvider.KAKAO, "provider-token");

            assertThat(providerId).isEqualTo("123456789");
            assertThat(stub.lastReceived().authorization()).isEqualTo("Bearer provider-token");
        }

        @Test
        void 중첩_경로에서도_식별자를_꺼낸다() {
            stub = new StubHttpServer().stub("/userinfo", 200,
                    "{\"resultcode\":\"00\",\"response\":{\"id\":\"naver-user-id\"}}");
            OAuthProviderProperties.Provider naver = new OAuthProviderProperties.Provider(
                    "client-id", "secret", "https://a", "https://t", stub.url("/userinfo"),
                    "openid", "response.id", true, OAuthProviderProperties.TokenRequestMethod.POST);
            OAuthClient client = new OAuthClient(
                    new OAuthProviderProperties(List.of(REDIRECT_URI), Map.of(AuthProvider.NAVER, naver)));

            assertThat(client.fetchProviderId(AuthProvider.NAVER, "provider-token"))
                    .isEqualTo("naver-user-id");
        }
    }
}
