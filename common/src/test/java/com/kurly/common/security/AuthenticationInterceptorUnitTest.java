package com.kurly.common.security;

import com.kurly.common.exception.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthenticationInterceptorUnitTest {

    private static final AuthenticatedPrincipal USER = new AuthenticatedPrincipal(1L, Role.USER);
    private static final AuthenticatedPrincipal ADMIN = new AuthenticatedPrincipal(2L, Role.ADMIN);

    @RestController
    static class ProbeController {

        @PublicApi
        @GetMapping("/open")
        String open() {
            return "open";
        }

        @Authenticated
        @GetMapping("/secured")
        String secured(@AuthPrincipal AuthenticatedPrincipal me) {
            return "user:" + me.userId();
        }

        @RequireRole(Role.ADMIN)
        @GetMapping("/admin-only")
        String adminOnly() {
            return "admin";
        }

        @GetMapping("/unannotated")
        String unannotated() {
            return "unannotated";
        }
    }

    /** 항상 지정된 주체를 돌려주는 검증기. 토큰 파싱 자체는 NimbusJwtVerifier의 책임이다. */
    private MockMvc mockMvcReturning(AuthenticatedPrincipal principal) {
        JwtVerifier verifier = token -> principal;
        return MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new AuthenticationInterceptor(verifier))
                .setCustomArgumentResolvers(new AuthPrincipalArgumentResolver())
                .build();
    }

    @Nested
    @DisplayName("공개 엔드포인트")
    class PublicApiTest {

        @Test
        void 토큰_없이_통과한다() throws Exception {
            mockMvcReturning(USER).perform(get("/open"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("open"));
        }
    }

    @Nested
    @DisplayName("인증이 필요한 엔드포인트")
    class AuthenticatedTest {

        @Test
        void 유효한_Bearer_토큰이면_통과하고_주체가_주입된다() throws Exception {
            mockMvcReturning(USER).perform(get("/secured").header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("user:1"));
        }
    }

    @Nested
    @DisplayName("역할 인가")
    class RequireRoleTest {

        @Test
        void 요구된_역할을_가지면_통과한다() throws Exception {
            mockMvcReturning(ADMIN).perform(get("/admin-only").header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("admin"));
        }
    }

    @Nested
    @DisplayName("애노테이션 누락 시 동작")
    class UnannotatedTest {

        @Test
        void 표기가_없으면_공개가_아니라_인증을_요구한다() throws Exception {
            // 기동 검사가 1차 방어이고, 런타임에서는 안전한 쪽으로 처리한다.
            mockMvcReturning(USER).perform(get("/unannotated"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }
    }

    @Nested
    @DisplayName("검증 대상이 아닌 요청")
    class BypassTest {

        /**
         * standalone MockMvc는 프리플라이트 핸들러 어댑터를 갖추지 않아 인터셉터까지 도달하지 못한다.
         * 인터셉터를 직접 호출해 검증한다.
         */
        @Test
        void 프리플라이트_요청은_검증기를_호출하지_않고_통과한다() throws Exception {
            AtomicBoolean verifierCalled = new AtomicBoolean(false);
            AuthenticationInterceptor interceptor = new AuthenticationInterceptor(token -> {
                verifierCalled.set(true);
                throw new IllegalStateException("호출되면 안 된다");
            });
            HandlerMethod protectedHandler = new HandlerMethod(
                    new ProbeController(), ProbeController.class.getDeclaredMethod("adminOnly"));

            MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/admin-only");
            request.addHeader("Origin", "https://example.com");
            request.addHeader("Access-Control-Request-Method", "GET");

            boolean proceed = interceptor.preHandle(request, new MockHttpServletResponse(), protectedHandler);

            assertThat(proceed).isTrue();
            assertThat(verifierCalled).isFalse();
        }

        @Test
        void 컨트롤러_핸들러가_아니면_통과한다() {
            AtomicBoolean verifierCalled = new AtomicBoolean(false);
            AuthenticationInterceptor interceptor = new AuthenticationInterceptor(token -> {
                verifierCalled.set(true);
                throw new IllegalStateException("호출되면 안 된다");
            });

            boolean proceed = interceptor.preHandle(
                    new MockHttpServletRequest("GET", "/static/app.js"),
                    new MockHttpServletResponse(),
                    new Object());

            assertThat(proceed).isTrue();
            assertThat(verifierCalled).isFalse();
        }
    }
}
