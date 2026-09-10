package com.kurly.payment.presentation.controller;

import com.kurly.common.exception.handler.GlobalExceptionHandler;
import com.kurly.common.security.AuthPrincipalArgumentResolver;
import com.kurly.common.security.AuthenticatedPrincipal;
import com.kurly.common.security.Role;
import com.kurly.payment.application.IdempotentRequestExecutor;
import com.kurly.payment.application.PaymentCheckoutService;
import com.kurly.payment.application.PaymentQueryService;
import com.kurly.payment.exception.PaymentNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerUnitExceptionTest {

    private static final AuthenticatedPrincipal ME = new AuthenticatedPrincipal(1L, Role.USER);
    private static final String VALID_BODY = """
            {"orderId":111,"paymentMethod":"CARD","paymentKey":"TOSS-1","amount":32000}""";

    @Mock PaymentCheckoutService paymentCheckoutService;
    @Mock PaymentQueryService paymentQueryService;
    @Mock IdempotentRequestExecutor idempotentRequestExecutor;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PaymentController(
                        paymentCheckoutService, paymentQueryService, idempotentRequestExecutor))
                .setCustomArgumentResolvers(new AuthPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("인증")
    class AuthTest {

        @Test
        void 주체가_주입되지_않으면_401이다() throws Exception {
            mockMvc.perform(get("/api/v1/payments/1/receipt"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }
    }

    @Nested
    @DisplayName("멱등키 헤더")
    class IdempotencyHeaderTest {

        @Test
        void 헤더가_없으면_400이다() throws Exception {
            // 처리기가 없으면 클라이언트가 헤더를 빠뜨렸을 뿐인데 500이 나간다.
            mockMvc.perform(post("/api/v1/payments/checkout")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME)
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_INPUT_VALUE"));
        }
    }

    @Nested
    @DisplayName("요청 검증")
    class ValidationTest {

        private void expectInvalid(String body) throws Exception {
            mockMvc.perform(post("/api/v1/payments/checkout")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME)
                            .header(PaymentController.IDEMPOTENCY_KEY_HEADER, "key-1")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_INPUT_VALUE"));
        }

        @Test
        void 주문_ID가_없으면_400이다() throws Exception {
            expectInvalid("""
                    {"paymentMethod":"CARD","paymentKey":"TOSS-1","amount":32000}""");
        }

        @Test
        void 결제_인증_토큰이_비면_400이다() throws Exception {
            expectInvalid("""
                    {"orderId":111,"paymentMethod":"CARD","paymentKey":"","amount":32000}""");
        }

        @Test
        void 금액이_0이면_400이다() throws Exception {
            expectInvalid("""
                    {"orderId":111,"paymentMethod":"CARD","paymentKey":"TOSS-1","amount":0}""");
        }

        @Test
        void 금액이_음수면_400이다() throws Exception {
            expectInvalid("""
                    {"orderId":111,"paymentMethod":"CARD","paymentKey":"TOSS-1","amount":-100}""");
        }
    }

    @Nested
    @DisplayName("조회 실패")
    class ReceiptTest {

        @Test
        void 없는_결제와_타인의_결제는_같은_404다() throws Exception {
            given(paymentQueryService.getOwnedPayment(anyLong(), anyLong()))
                    .willThrow(new PaymentNotFoundException());

            mockMvc.perform(get("/api/v1/payments/99999/receipt")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("PAYMENT_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("존재하지 않는 결제 내역입니다."));
        }
    }
}
