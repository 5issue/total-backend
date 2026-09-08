package com.kurly.auth.infrastructure.oauth;

import com.kurly.auth.infrastructure.security.RefreshTokenCookieProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthTransactionCookiesUnitTest {

    private final OAuthTransactionCookies cookies = new OAuthTransactionCookies(
            new RefreshTokenCookieProperties("refresh_token", "/api/v1/auth/refresh", true, "Strict"));

    private static final OAuthTransaction TRANSACTION =
            new OAuthTransaction("state-v", "verifier-v", "http://localhost/callback");

    @Nested
    @DisplayName("쿠키 발급")
    class CreateTest {

        @Test
        void 세_값이_모두_쿠키로_내려간다() {
            List<ResponseCookie> created = cookies.create(TRANSACTION);

            assertThat(created).extracting(ResponseCookie::getName)
                    .containsExactlyInAnyOrder("oauth_state", "oauth_code_verifier", "oauth_redirect_uri");
        }

        @Test
        void SameSite는_Lax다() {
            // 소셜 제공자에서 돌아오는 흐름은 크로스사이트라 Strict면 쿠키가 전송되지 않는다.
            assertThat(cookies.create(TRANSACTION))
                    .allSatisfy(cookie -> assertThat(cookie.getSameSite()).isEqualTo("Lax"));
        }

        @Test
        void HttpOnly이며_콜백_경로로_제한된다() {
            assertThat(cookies.create(TRANSACTION)).allSatisfy(cookie -> {
                assertThat(cookie.isHttpOnly()).isTrue();
                assertThat(cookie.getPath()).isEqualTo("/api/v1/auth/oauth");
            });
        }

        @Test
        void Secure는_refresh_쿠키_설정을_따른다() {
            assertThat(cookies.create(TRANSACTION)).allSatisfy(c -> assertThat(c.isSecure()).isTrue());

            OAuthTransactionCookies insecure = new OAuthTransactionCookies(
                    new RefreshTokenCookieProperties("refresh_token", "/p", false, "Strict"));
            assertThat(insecure.create(TRANSACTION)).allSatisfy(c -> assertThat(c.isSecure()).isFalse());
        }
    }

    @Nested
    @DisplayName("쿠키 만료")
    class ExpireTest {

        @Test
        void 세_쿠키를_즉시_만료시킨다() {
            List<ResponseCookie> expired = cookies.expire();

            assertThat(expired).hasSize(3)
                    .allSatisfy(cookie -> assertThat(cookie.getMaxAge().isZero()).isTrue());
        }
    }

    @Nested
    @DisplayName("쿠키 추출")
    class ExtractTest {

        @Test
        void 세_값이_모두_있으면_복원된다() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setCookies(
                    new Cookie("oauth_state", "state-v"),
                    new Cookie("oauth_code_verifier", "verifier-v"),
                    new Cookie("oauth_redirect_uri", "http://localhost/callback"));

            Optional<OAuthTransaction> extracted = cookies.extract(request);

            assertThat(extracted).contains(TRANSACTION);
        }

        @Test
        void 쿠키가_아예_없으면_비어_있다() {
            assertThat(cookies.extract(new MockHttpServletRequest())).isEmpty();
        }

        @Test
        void 일부만_있으면_비어_있다() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setCookies(new Cookie("oauth_state", "state-v"));

            assertThat(cookies.extract(request)).isEmpty();
        }

        @Test
        void 값이_비어_있으면_없는_것으로_본다() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setCookies(
                    new Cookie("oauth_state", ""),
                    new Cookie("oauth_code_verifier", "verifier-v"),
                    new Cookie("oauth_redirect_uri", "http://localhost/callback"));

            assertThat(cookies.extract(request)).isEmpty();
        }
    }
}
