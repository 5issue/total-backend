package com.kurly.common.security;

import com.kurly.common.exception.UnauthorizedException;
import com.kurly.common.exception.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthenticationInterceptorUnitExceptionTest {

    @RestController
    static class ProbeController {

        @Authenticated
        @GetMapping("/secured")
        String secured() {
            return "secured";
        }

        @RequireRole(Role.ADMIN)
        @GetMapping("/admin-only")
        String adminOnly() {
            return "admin";
        }

        @PublicApi
        @GetMapping("/open-but-wants-principal")
        String openButWantsPrincipal(@AuthPrincipal AuthenticatedPrincipal me) {
            return "should not reach";
        }
    }

    private MockMvc mockMvc(JwtVerifier verifier) {
        return MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new AuthenticationInterceptor(verifier))
                .setCustomArgumentResolvers(new AuthPrincipalArgumentResolver())
                .build();
    }

    private MockMvc withPrincipal(Role role) {
        return mockMvc(token -> new AuthenticatedPrincipal(1L, role));
    }

    private MockMvc withRejectingVerifier() {
        return mockMvc(token -> {
            throw new UnauthorizedException();
        });
    }

    @Nested
    @DisplayName("토큰 추출 실패")
    class BearerTokenTest {

        @Test
        void Authorization_헤더가_없으면_401() throws Exception {
            withPrincipal(Role.USER).perform(get("/secured"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }

        @Test
        void Bearer_접두사가_없으면_401() throws Exception {
            withPrincipal(Role.USER).perform(get("/secured").header("Authorization", "some-token"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void Bearer_뒤가_비어_있으면_401() throws Exception {
            withPrincipal(Role.USER).perform(get("/secured").header("Authorization", "Bearer   "))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("토큰 검증 실패")
    class VerificationTest {

        @Test
        void 검증기가_거부하면_401이고_사유는_노출되지_않는다() throws Exception {
            withRejectingVerifier().perform(get("/secured").header("Authorization", "Bearer bad-token"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.message").value("인증이 필요합니다."));
        }
    }

    @Nested
    @DisplayName("역할 부족")
    class ForbiddenTest {

        @Test
        void 인증은_됐지만_역할이_부족하면_401이_아니라_403() throws Exception {
            withPrincipal(Role.USER).perform(get("/admin-only").header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }
    }

    @Nested
    @DisplayName("설정 실수")
    class MisconfigurationTest {

        @Test
        void 공개_엔드포인트에서_주체를_요구하면_통과시키지_않는다() throws Exception {
            withPrincipal(Role.USER).perform(get("/open-but-wants-principal"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
