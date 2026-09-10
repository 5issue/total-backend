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
                        new RefreshTokenCookieFactory(cookieProperties)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static TokenPair tokenPair() {
        return new TokenPair(
                new IssuedToken("access-token", Instant.now().plusSeconds(1800), Duration.ofMinutes(30), "a"),
                new IssuedToken("refresh-token", Instant.now().plusSeconds(1209600), Duration.ofDays(14), "r"));
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
        void 기존_회원은_200이다() throws Exception {
            given(socialAuthService.handleCallback(any(), any(), any(), any()))
                    .willReturn(new SocialLoginResult(tokenPair(), 50001L, false));

            mockMvc.perform(get("/api/v1/auth/oauth/kakao/callback")
                            .param("code", "c").param("state", "state-v")
                            .cookie(transactionCookies()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("소셜 로그인이 완료되었습니다."))
                    .andExpect(jsonPath("$.data.user.userId").value(50001))
                    .andExpect(jsonPath("$.data.expiresIn").value(1800));
        }

        @Test
        void 신규_회원은_201이다() throws Exception {
            given(socialAuthService.handleCallback(any(), any(), any(), any()))
                    .willReturn(new SocialLoginResult(tokenPair(), 70001L, true));

            mockMvc.perform(get("/api/v1/auth/oauth/kakao/callback")
                            .param("code", "c").param("state", "state-v")
                            .cookie(transactionCookies()))
                    .andExpect(status().isCreated());
        }

        @Test
        void 토큰이_실린_응답은_캐시되지_않는다() throws Exception {
            given(socialAuthService.handleCallback(any(), any(), any(), any()))
                    .willReturn(new SocialLoginResult(tokenPair(), 1L, false));

            mockMvc.perform(get("/api/v1/auth/oauth/kakao/callback")
                            .param("code", "c").param("state", "state-v")
                            .cookie(transactionCookies()))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        }

        @Test
        void 컨텍스트_쿠키와_refresh_쿠키를_함께_내려준다() throws Exception {
            given(socialAuthService.handleCallback(any(), any(), any(), any()))
                    .willReturn(new SocialLoginResult(tokenPair(), 1L, false));

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
        void 컨텍스트_쿠키가_없으면_401이다() throws Exception {
            mockMvc.perform(get("/api/v1/auth/oauth/kakao/callback")
                            .param("code", "c").param("state", "s"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }

        @Test
        void 콜백도_지원하지_않는_제공자는_400이다() throws Exception {
            mockMvc.perform(get("/api/v1/auth/oauth/google/callback")
                            .param("code", "c").param("state", "s")
                            .cookie(transactionCookies()))
                    .andExpect(status().isBadRequest());
        }
    }
}
