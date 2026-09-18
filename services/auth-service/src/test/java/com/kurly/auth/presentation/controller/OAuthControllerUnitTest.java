package com.kurly.auth.presentation.controller;

import com.kurly.auth.application.SocialAuthService;
import com.kurly.auth.application.dto.SocialLoginResult;
import com.kurly.auth.application.dto.TokenPair;
import com.kurly.auth.domain.enums.AuthProvider;
import com.kurly.auth.infrastructure.oauth.OAuthTransaction;
import com.kurly.auth.infrastructure.oauth.OAuthTransactionCookies;
import com.kurly.auth.infrastructure.security.RefreshTokenCookieFactory;
import com.kurly.auth.infrastructure.security.RefreshTokenCookieProperties;
import com.kurly.auth.infrastructure.security.jwt.IssuedToken;
import com.kurly.common.exception.handler.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OAuthControllerUnitTest {

    private static final String REDIRECT = "http://localhost:8081/api/v1/auth/oauth/kakao/callback";
    private static final String FRONTEND = "http://localhost:3000/";
    private static final String BODY = """
            {"redirectUri":"http://localhost:8081/api/v1/auth/oauth/kakao/callback"}""";

    @Mock SocialAuthService socialAuthService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RefreshTokenCookieProperties cookieProperties =
                new RefreshTokenCookieProperties("refresh_token", "/api/v1/auth/refresh", true, "Strict");
        mockMvc = MockMvcBuilders.standaloneSetup(new OAuthController(
                        socialAuthService,
                        new OAuthTransactionCookies(cookieProperties),
                        new RefreshTokenCookieFactory(cookieProperties),
                        FRONTEND))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static TokenPair tokenPair() {
        return new TokenPair(
                new IssuedToken("access-token", Instant.now().plusSeconds(1800), Duration.ofMinutes(30), "a"),
                new IssuedToken("refresh-token", Instant.now().plusSeconds(1209600), Duration.ofDays(14), "r"),
                1001L);
    }

    private static Cookie[] transactionCookies() {
        return new Cookie[]{
                new Cookie("oauth_state", "state-v"),
                new Cookie("oauth_code_verifier", "verifier-v"),
                new Cookie("oauth_redirect_uri", REDIRECT)};
    }

    @Nested
    @DisplayName("인가 URL 발급")
    class LoginUrlTest {

        @Test
        void 로그인_URL과_컨텍스트_쿠키를_내려준다() throws Exception {
            given(socialAuthService.createAuthorizationRequest(AuthProvider.KAKAO, REDIRECT))
                    .willReturn(new SocialAuthService.AuthorizationRequest(
                            "https://provider/login", new OAuthTransaction("s", "v", REDIRECT)));

            MvcResult result = mockMvc.perform(post("/api/v1/auth/oauth/kakao")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("소셜 로그인 URL"))
                    .andExpect(jsonPath("$.data.loginUrl").value("https://provider/login"))
                    .andExpect(jsonPath("$.data.provider").value("KAKAO"))
                    .andReturn();

            assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                    .anyMatch(c -> c.startsWith("oauth_state="))
                    .anyMatch(c -> c.startsWith("oauth_code_verifier="));
        }

        @Test
        void 제공자_이름은_대소문자를_가리지_않는다() throws Exception {
            given(socialAuthService.createAuthorizationRequest(AuthProvider.KAKAO, REDIRECT))
                    .willReturn(new SocialAuthService.AuthorizationRequest(
                            "https://provider/login", new OAuthTransaction("s", "v", REDIRECT)));

            mockMvc.perform(post("/api/v1/auth/oauth/KAKAO")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isOk());
        }

        @Test
        void 지원하지_않는_제공자는_400이다() throws Exception {
            mockMvc.perform(post("/api/v1/auth/oauth/google")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                    .andExpect(jsonPath("$.message").value("지원하지 않는 서비스 제공자입니다."));
        }

        @Test
        void redirectUri가_비면_400이다() throws Exception {
            mockMvc.perform(post("/api/v1/auth/oauth/kakao")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"redirectUri\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_INPUT_VALUE"));
        }
    }

    @Nested
    @DisplayName("콜백 처리")
    class CallbackTest {

        @Test
        void 성공하면_프론트로_302_리다이렉트한다() throws Exception {
            given(socialAuthService.handleCallback(any(), any(), any(), any()))
                    .willReturn(new SocialLoginResult(tokenPair(), 50001L));

            mockMvc.perform(get("/api/v1/auth/oauth/kakao/callback")
                            .param("code", "c").param("state", "state-v")
                            .cookie(transactionCookies()))
                    .andExpect(status().isFound())
                    .andExpect(header().string(HttpHeaders.LOCATION,
                            "http://localhost:3000/?login=success"));
        }

        @Test
        void access_token은_리다이렉트_URL에_싣지_않는다() throws Exception {
            given(socialAuthService.handleCallback(any(), any(), any(), any()))
                    .willReturn(new SocialLoginResult(tokenPair(), 1L));

            MvcResult result = mockMvc.perform(get("/api/v1/auth/oauth/kakao/callback")
                            .param("code", "c").param("state", "state-v")
                            .cookie(transactionCookies()))
                    .andReturn();

            // 설계서 1.4 — access는 메모리 보관. URL·히스토리·Referer 어디에도 남으면 안 된다.
            assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                    .doesNotContain("access-token")
                    .doesNotContain("refresh-token");
        }

        @Test
        void 토큰이_실린_응답은_캐시되지_않는다() throws Exception {
            given(socialAuthService.handleCallback(any(), any(), any(), any()))
                    .willReturn(new SocialLoginResult(tokenPair(), 1L));

            mockMvc.perform(get("/api/v1/auth/oauth/kakao/callback")
                            .param("code", "c").param("state", "state-v")
                            .cookie(transactionCookies()))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        }

        @Test
        void 컨텍스트_쿠키와_refresh_쿠키를_함께_내려준다() throws Exception {
            given(socialAuthService.handleCallback(any(), any(), any(), any()))
                    .willReturn(new SocialLoginResult(tokenPair(), 1L));

            MvcResult result = mockMvc.perform(get("/api/v1/auth/oauth/kakao/callback")
                            .param("code", "c").param("state", "state-v")
                            .cookie(transactionCookies()))
                    .andReturn();

            assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                    // 1회용 컨텍스트는 만료시키고, refresh 쿠키는 새로 내려간다.
                    .anyMatch(c -> c.startsWith("oauth_state=") && c.contains("Max-Age=0"))
                    .anyMatch(c -> c.startsWith("refresh_token=") && c.contains("Max-Age=1209600"));
        }

        @Test
        void 컨텍스트_쿠키가_없어도_JSON이_아니라_실패_리다이렉트다() throws Exception {
            mockMvc.perform(get("/api/v1/auth/oauth/kakao/callback")
                            .param("code", "c").param("state", "s"))
                    .andExpect(status().isFound())
                    .andExpect(header().string(HttpHeaders.LOCATION,
                            "http://localhost:3000/?login=failed"));
        }

        @Test
        void 사용자가_동의를_취소하면_실패_리다이렉트다() throws Exception {
            // 제공자는 code 대신 error를 싣고 되돌려보낸다.
            mockMvc.perform(get("/api/v1/auth/oauth/kakao/callback")
                            .param("error", "access_denied").param("state", "state-v")
                            .cookie(transactionCookies()))
                    .andExpect(status().isFound())
                    .andExpect(header().string(HttpHeaders.LOCATION,
                            "http://localhost:3000/?login=failed"));
        }

        @Test
        void 콜백도_지원하지_않는_제공자는_실패_리다이렉트다() throws Exception {
            mockMvc.perform(get("/api/v1/auth/oauth/google/callback")
                            .param("code", "c").param("state", "s")
                            .cookie(transactionCookies()))
                    .andExpect(status().isFound())
                    .andExpect(header().string(HttpHeaders.LOCATION,
                            "http://localhost:3000/?login=failed"));
        }

        @Test
        void 실패해도_1회용_컨텍스트_쿠키는_만료시킨다() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/v1/auth/oauth/kakao/callback")
                            .param("error", "access_denied")
                            .cookie(transactionCookies()))
                    .andReturn();

            assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                    .anyMatch(c -> c.startsWith("oauth_state=") && c.contains("Max-Age=0"))
                    .noneMatch(c -> c.startsWith("refresh_token="));
        }
    }
}
