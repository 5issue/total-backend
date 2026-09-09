package com.kurly.auth.presentation.controller;

import com.jayway.jsonpath.JsonPath;
import com.kurly.auth.domain.entity.AuthUser;
import com.kurly.auth.domain.entity.UserRefreshToken;
import com.kurly.auth.domain.enums.AuthProvider;
import com.kurly.common.security.Role;
import com.kurly.auth.domain.entity.AuthAdmin;
import com.kurly.auth.domain.repository.AuthAdminRepository;
import com.kurly.auth.domain.repository.AuthUserRepository;
import com.kurly.auth.domain.repository.UserRefreshTokenRepository;
import com.kurly.auth.infrastructure.persistence.AdminRefreshTokenJpaRepository;
import com.kurly.auth.infrastructure.persistence.AuthAdminJpaRepository;
import com.kurly.auth.infrastructure.persistence.AuthUserJpaRepository;
import com.kurly.auth.infrastructure.persistence.UserRefreshTokenJpaRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.kurly.auth.infrastructure.security.RefreshTokenHasher;
import com.kurly.auth.infrastructure.security.jwt.IssuedToken;
import com.kurly.auth.infrastructure.security.jwt.JwtTokenProvider;
import com.kurly.common.security.TokenType;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API 명세와의 일치 여부를 응답 헤더·본문 수준에서 확인한다.
 * 로컬 MySQL이 필요하므로 {@code AUTH_INTEGRATION_TEST=true}일 때만 실행된다.
 */
// 쿠키 Secure는 명세가 요구하는 보안 속성이므로, 로컬 프로파일이 무엇으로 두든
// 테스트에서는 항상 켠 상태로 검증한다.
@SpringBootTest(properties = "auth.refresh-cookie.secure=true")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "AUTH_INTEGRATION_TEST", matches = "true")
class AuthControllerIntegrationTest {

    private static final String REFRESH_PATH = "/api/v1/auth/refresh";
    private static final String ADMIN_LOGIN_PATH = "/api/v1/auth/admin/login";
    private static final String LOGOUT_PATH = "/api/v1/auth/logout";

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired RefreshTokenHasher refreshTokenHasher;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AuthUserRepository authUserRepository;
    @Autowired UserRefreshTokenRepository userRefreshTokenRepository;
    @Autowired AuthAdminRepository authAdminRepository;
    @Autowired AuthUserJpaRepository authUserJpaRepository;
    @Autowired UserRefreshTokenJpaRepository userRefreshTokenJpaRepository;
    @Autowired AuthAdminJpaRepository authAdminJpaRepository;
    @Autowired AdminRefreshTokenJpaRepository adminRefreshTokenJpaRepository;

    @AfterEach
    void cleanUp() {
        userRefreshTokenJpaRepository.deleteAll();
        authUserJpaRepository.deleteAll();
        adminRefreshTokenJpaRepository.deleteAll();
        authAdminJpaRepository.deleteAll();
    }

    private String givenStoredRefreshToken() {
        AuthUser user = authUserRepository.save(AuthUser.builder()
                .provider(AuthProvider.KAKAO)
                .providerId("kakao-" + System.nanoTime())
                .userId(System.nanoTime() % 1_000_000)
                .build());
        IssuedToken refresh = jwtTokenProvider.issueRefreshToken(user.getId(), Role.USER);
        userRefreshTokenRepository.save(UserRefreshToken.builder()
                .token(refreshTokenHasher.hash(refresh.token()))
                .expiresAt(LocalDateTime.ofInstant(refresh.expiresAt(), ZoneId.systemDefault()))
                .authUser(user)
                .build());
        return refresh.token();
    }

    private void givenAdmin(String loginId, String rawPassword) {
        authAdminRepository.save(AuthAdmin.builder()
                .loginId(loginId)
                .password(passwordEncoder.encode(rawPassword))
                .adminId(1001L)
                .build());
    }

    /**
     * 응답 계약을 그대로 검증한다. 정규식으로 본문 전체에서 accessToken을 찾으면
     * $.data 밖에 있는 값도 통과시켜, 계약이 깨져도 테스트가 초록으로 남는다.
     */
    private static String accessTokenOf(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    @Nested
    @DisplayName("관리자 로그인")
    class AdminLoginTest {

        @Test
        void 정상_자격증명이면_access_token과_refresh_쿠키를_받는다() throws Exception {
            givenAdmin("admin1", "Str0ng!Password");

            MvcResult result = mockMvc.perform(post(ADMIN_LOGIN_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"loginId":"admin1","password":"Str0ng!Password"}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.message").value("관리자로그인이 완료되었습니다."))
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.expiresIn").value(1800))
                    .andExpect(jsonPath("$.error").isEmpty())
                    .andReturn();

            String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
            assertThat(setCookie).isNotNull();
            assertThat(setCookie).startsWith("refresh_token=");
            assertThat(setCookie).contains("Path=/api/v1/auth/refresh");
            assertThat(setCookie).contains("HttpOnly");
            assertThat(setCookie).contains("Secure");
            assertThat(setCookie).contains("SameSite=Strict");
            assertThat(setCookie).contains("Max-Age=1209600");
        }

        @Test
        void 발급된_access_token은_ADMIN_역할을_가진다() throws Exception {
            givenAdmin("admin1", "Str0ng!Password");

            MvcResult result = mockMvc.perform(post(ADMIN_LOGIN_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"loginId":"admin1","password":"Str0ng!Password"}"""))
                    .andExpect(status().isOk())
                    .andReturn();

            String accessToken = accessTokenOf(result);
            assertThat(jwtTokenProvider.parse(accessToken, TokenType.ACCESS).role()).isEqualTo(Role.ADMIN);
        }

        @Test
        void 로그인으로_받은_쿠키로_재발급까지_이어진다() throws Exception {
            givenAdmin("admin1", "Str0ng!Password");

            MvcResult login = mockMvc.perform(post(ADMIN_LOGIN_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"loginId":"admin1","password":"Str0ng!Password"}"""))
                    .andExpect(status().isOk())
                    .andReturn();

            jakarta.servlet.http.Cookie issued = login.getResponse().getCookie("refresh_token");
            assertThat(issued).isNotNull();

            mockMvc.perform(post(REFRESH_PATH).cookie(issued))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
        }

        @Test
        void 비밀번호가_틀리면_401과_동일한_메시지를_반환한다() throws Exception {
            givenAdmin("admin1", "Str0ng!Password");

            mockMvc.perform(post(ADMIN_LOGIN_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"loginId":"admin1","password":"wrong"}"""))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 일치하지 않습니다."));
        }

        @Test
        void 없는_아이디도_같은_401_메시지를_반환한다() throws Exception {
            mockMvc.perform(post(ADMIN_LOGIN_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"loginId":"nobody","password":"whatever"}"""))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 일치하지 않습니다."));
        }

        @Test
        void 필수값이_비면_400을_반환한다() throws Exception {
            mockMvc.perform(post(ADMIN_LOGIN_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"loginId":"","password":""}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_INPUT_VALUE"));
        }

        @Test
        void 비밀번호는_평문으로_저장되지_않는다() {
            givenAdmin("admin1", "Str0ng!Password");

            AuthAdmin saved = authAdminRepository.findByLoginId("admin1").orElseThrow();

            assertThat(saved.getPassword()).isNotEqualTo("Str0ng!Password");
            assertThat(saved.getPassword()).startsWith("$2");
        }
    }


    @Nested
    @DisplayName("로그아웃")
    class LogoutTest {

        private String loginAndGetAccessToken() throws Exception {
            givenAdmin("admin1", "Str0ng!Password");
            MvcResult login = mockMvc.perform(post(ADMIN_LOGIN_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"loginId":"admin1","password":"Str0ng!Password"}"""))
                    .andExpect(status().isOk())
                    .andReturn();
            return accessTokenOf(login);
        }

        @Test
        void 토큰이_없으면_401이다() throws Exception {
            mockMvc.perform(post(LOGOUT_PATH))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }

        @Test
        void 유효한_토큰이면_200과_공통_응답_포맷을_반환한다() throws Exception {
            String accessToken = loginAndGetAccessToken();

            mockMvc.perform(post(LOGOUT_PATH).header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.message").value("성공적으로 로그아웃 되었습니다."))
                    .andExpect(jsonPath("$.error").isEmpty());
        }

        @Test
        void 세션이_제거되어_기존_refresh_쿠키로_재발급할_수_없다() throws Exception {
            givenAdmin("admin1", "Str0ng!Password");
            MvcResult login = mockMvc.perform(post(ADMIN_LOGIN_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"loginId":"admin1","password":"Str0ng!Password"}"""))
                    .andExpect(status().isOk())
                    .andReturn();
            Cookie refreshCookie = login.getResponse().getCookie("refresh_token");
            String accessToken = accessTokenOf(login);

            // 로그아웃 전에는 재발급이 된다는 것을 먼저 확인하지 않는다(회전되어 쿠키가 바뀌므로).
            mockMvc.perform(post(LOGOUT_PATH).header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk());

            assertThat(refreshCookie).isNotNull();
            mockMvc.perform(post(REFRESH_PATH).cookie(refreshCookie))
                    .andExpect(status().isUnauthorized());

            assertThat(adminRefreshTokenJpaRepository.count()).isZero();
        }

        @Test
        void refresh_쿠키를_만료시키는_Set_Cookie를_내려준다() throws Exception {
            String accessToken = loginAndGetAccessToken();

            MvcResult result = mockMvc.perform(post(LOGOUT_PATH)
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andReturn();

            String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
            assertThat(setCookie).isNotNull();
            assertThat(setCookie).contains("refresh_token=");
            assertThat(setCookie).contains("Max-Age=0");
            // 삭제되려면 발급 시와 Path가 같아야 한다.
            assertThat(setCookie).contains("Path=/api/v1/auth/refresh");
        }

        @Test
        void refresh_token으로는_로그아웃할_수_없다() throws Exception {
            givenAdmin("admin1", "Str0ng!Password");
            MvcResult login = mockMvc.perform(post(ADMIN_LOGIN_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"loginId":"admin1","password":"Str0ng!Password"}"""))
                    // 상태를 단정하지 않으면 로그인이 실패해도 쿠키만 있으면 뒤 검증이 통과한다.
                    .andExpect(status().isOk())
                    .andReturn();
            Cookie refreshCookie = login.getResponse().getCookie("refresh_token");
            assertThat(refreshCookie).isNotNull();
            String refreshToken = refreshCookie.getValue();

            mockMvc.perform(post(LOGOUT_PATH).header("Authorization", "Bearer " + refreshToken))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("성공 응답 규격")
    class SuccessTest {

        @Test
        void 명세대로_응답_본문이_구성된다() throws Exception {
            String refreshToken = givenStoredRefreshToken();

            mockMvc.perform(post(REFRESH_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}")
                            .cookie(new Cookie("refresh_token", refreshToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.message").value("토큰이 성공적으로 재발급되었습니다."))
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.expiresIn").value(1800))
                    .andExpect(jsonPath("$.error").isEmpty())
                    .andExpect(jsonPath("$.timestamp").isNotEmpty());
        }

        @Test
        void 명세대로_refresh_token_쿠키가_회전되어_내려간다() throws Exception {
            String refreshToken = givenStoredRefreshToken();

            MvcResult result = mockMvc.perform(post(REFRESH_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}")
                            .cookie(new Cookie("refresh_token", refreshToken)))
                    .andExpect(status().isOk())
                    .andReturn();

            String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
            assertThat(setCookie).isNotNull();
            assertThat(setCookie).startsWith("refresh_token=");
            assertThat(setCookie).contains("Path=/api/v1/auth/refresh");
            assertThat(setCookie).contains("HttpOnly");
            assertThat(setCookie).contains("Secure");
            assertThat(setCookie).contains("SameSite=Strict");
            assertThat(setCookie).contains("Max-Age=1209600");
            assertThat(setCookie).doesNotContain("refresh_token=" + refreshToken);
        }
    }

    @Nested
    @DisplayName("실패 응답 규격")
    class FailureTest {

        @Test
        void 쿠키가_없으면_명세대로_401을_반환한다() throws Exception {
            mockMvc.perform(post(REFRESH_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value("ERROR"))
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.message")
                            .value("유효하지 않거나 만료된 리프레시 토큰입니다. 다시 로그인해주세요."))
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        void 폐기된_토큰이면_명세대로_401을_반환한다() throws Exception {
            String refreshToken = givenStoredRefreshToken();
            mockMvc.perform(post(REFRESH_PATH).cookie(new Cookie("refresh_token", refreshToken)));

            mockMvc.perform(post(REFRESH_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}")
                            .cookie(new Cookie("refresh_token", refreshToken)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.message")
                            .value("유효하지 않거나 만료된 리프레시 토큰입니다. 다시 로그인해주세요."));
        }
    }
}
