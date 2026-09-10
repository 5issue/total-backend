package com.kurly.payment.presentation.controller;

import com.kurly.common.exception.handler.GlobalExceptionHandler;
import com.kurly.common.security.AuthPrincipalArgumentResolver;
import com.kurly.common.security.AuthenticatedPrincipal;
import com.kurly.common.security.Role;
import com.kurly.payment.application.IdempotentRequestExecutor;
import com.kurly.payment.application.PaymentCancelService;
import com.kurly.payment.domain.entity.Payment;
import com.kurly.payment.domain.entity.PaymentCancel;
import com.kurly.payment.exception.InvalidPaymentStatusException;
import com.kurly.payment.exception.PaymentNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentInternalControllerUnitTest {

    private static final AuthenticatedPrincipal ME = new AuthenticatedPrincipal(1L, Role.USER);
    private static final String BODY = """
            {"cancelReason":"USER_CANCEL"}""";

    @Mock PaymentCancelService paymentCancelService;
    @Mock IdempotentRequestExecutor idempotentRequestExecutor;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new PaymentInternalController(paymentCancelService, idempotentRequestExecutor))
                .setCustomArgumentResolvers(new AuthPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static PaymentCancel canceled() {
        Payment payment = Payment.builder().orderId(111L).userId(1L).totalAmount(32_000L).build();
        ReflectionTestUtils.setField(payment, "id", 1L);
        payment.approve("TOSS-1", "CARD", "https://toss.im/r/1");
        payment.cancel();
        PaymentCancel cancel = PaymentCancel.builder()
                .payment(payment).cancelReason("USER_CANCEL").cancelAmount(32_000L).build();
        ReflectionTestUtils.setField(cancel, "id", 20L);
        return cancel;
    }

    @SuppressWarnings("unchecked")
    private void passThroughExecutor() {
        given(idempotentRequestExecutor.execute(anyLong(), anyString(), anyString(), any(), any(), anyInt(), any()))
                .willAnswer(invocation -> {
                    Supplier<Object> action = invocation.getArgument(6);
                    return new IdempotentRequestExecutor.Outcome<>(HttpStatus.OK.value(), action.get(), false);
                });
    }

    @Nested
    @DisplayName("POST /internal/v1/payments/{paymentId}/cancel")
    class CancelTest {

        @Test
        void 취소_결과를_내려준다() throws Exception {
            passThroughExecutor();
            given(paymentCancelService.cancel(1L, 1L, "USER_CANCEL")).willReturn(canceled());

            mockMvc.perform(post("/internal/v1/payments/1/cancel")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME)
                            .header(PaymentController.IDEMPOTENCY_KEY_HEADER, "key-1")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("결제가 정상적으로 취소되었습니다."))
                    .andExpect(jsonPath("$.data.paymentId").value(1))
                    .andExpect(jsonPath("$.data.status").value("CANCELED"))
                    .andExpect(jsonPath("$.data.canceledAt").isNotEmpty());
        }

        @Test
        void 소유자는_전파된_토큰에서_가져온다() throws Exception {
            // 내부 경로지만 사용자 토큰이 전파되므로 소유권 검사가 성립한다(설계서 3.4).
            passThroughExecutor();
            given(paymentCancelService.cancel(anyLong(), anyLong(), anyString())).willReturn(canceled());

            mockMvc.perform(post("/internal/v1/payments/1/cancel")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME)
                            .header(PaymentController.IDEMPOTENCY_KEY_HEADER, "key-1")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isOk());

            verify(paymentCancelService).cancel(eq(1L), eq(1L), eq("USER_CANCEL"));
        }
    }

    @Nested
    @DisplayName("실패")
    class FailureTest {

        @Test
        void 멱등키_헤더가_없으면_400이다() throws Exception {
            mockMvc.perform(post("/internal/v1/payments/1/cancel")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME)
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 취소_사유가_비면_400이다() throws Exception {
            mockMvc.perform(post("/internal/v1/payments/1/cancel")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME)
                            .header(PaymentController.IDEMPOTENCY_KEY_HEADER, "key-1")
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"cancelReason":""}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_INPUT_VALUE"));
        }

        @Test
        void 없는_결제와_타인의_결제는_같은_404다() throws Exception {
            passThroughExecutor();
            given(paymentCancelService.cancel(anyLong(), anyLong(), anyString()))
                    .willThrow(new PaymentNotFoundException());

            mockMvc.perform(post("/internal/v1/payments/99999/cancel")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME)
                            .header(PaymentController.IDEMPOTENCY_KEY_HEADER, "key-1")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("PAYMENT_NOT_FOUND"));
        }

        @Test
        void 이미_취소된_결제는_400이다() throws Exception {
            passThroughExecutor();
            given(paymentCancelService.cancel(anyLong(), anyLong(), anyString()))
                    .willThrow(new InvalidPaymentStatusException());

            mockMvc.perform(post("/internal/v1/payments/1/cancel")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME)
                            .header(PaymentController.IDEMPOTENCY_KEY_HEADER, "key-1")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_PAYMENT_STATUS"));
        }
    }
}
