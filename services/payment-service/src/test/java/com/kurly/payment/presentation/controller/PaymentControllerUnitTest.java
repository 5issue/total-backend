package com.kurly.payment.presentation.controller;

import com.kurly.common.exception.handler.GlobalExceptionHandler;
import com.kurly.common.security.AuthPrincipalArgumentResolver;
import com.kurly.common.security.AuthenticatedPrincipal;
import com.kurly.common.security.Role;
import com.kurly.payment.application.IdempotentRequestExecutor;
import com.kurly.payment.application.PaymentCheckoutService;
import com.kurly.payment.application.PaymentQueryService;
import com.kurly.payment.domain.entity.Payment;
import com.kurly.payment.presentation.dto.CheckoutResponse;
import com.kurly.payment.presentation.dto.ReceiptResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerUnitTest {

    private static final AuthenticatedPrincipal ME = new AuthenticatedPrincipal(1L, Role.USER);
    private static final String BODY = """
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

    private static Payment approvedPayment() {
        Payment payment = Payment.builder().orderId(111L).userId(1L).totalAmount(32_000L).build();
        ReflectionTestUtils.setField(payment, "id", 1L);
        payment.approve("TOSS-1", "CARD", "https://toss.im/r/1");
        return payment;
    }

    /** 실행기는 감싸기만 하므로, 테스트에서는 실제 처리를 그대로 실행시킨다. */
    @SuppressWarnings("unchecked")
    private void passThroughExecutor() {
        given(idempotentRequestExecutor.execute(anyLong(), anyString(), anyString(), any(), any(), anyInt(), any()))
                .willAnswer(invocation -> {
                    Supplier<Object> action = invocation.getArgument(6);
                    return new IdempotentRequestExecutor.Outcome<>(
                            HttpStatus.OK.value(), action.get(), false);
                });
    }

    @Nested
    @DisplayName("POST /checkout")
    class CheckoutTest {

        @Test
        void 승인_결과를_내려준다() throws Exception {
            passThroughExecutor();
            given(paymentCheckoutService.checkout(eq(1L), eq(111L), eq("TOSS-1"), eq(32_000L)))
                    .willReturn(approvedPayment());

            mockMvc.perform(post("/api/v1/payments/checkout")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME)
                            .header(PaymentController.IDEMPOTENCY_KEY_HEADER, "key-1")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("결제가 성공적으로 승인 및 완료되었습니다."))
                    .andExpect(jsonPath("$.data.paymentStatus").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.receiptUrl").value("https://toss.im/r/1"))
                    .andExpect(jsonPath("$.data.paymentCompletedAt").isNotEmpty());
        }

        @Test
        void 영수증_주소가_실리므로_캐시를_막는다() throws Exception {
            passThroughExecutor();
            given(paymentCheckoutService.checkout(anyLong(), anyLong(), anyString(), anyLong()))
                    .willReturn(approvedPayment());

            mockMvc.perform(post("/api/v1/payments/checkout")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME)
                            .header(PaymentController.IDEMPOTENCY_KEY_HEADER, "key-1")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        }

        @Test
        void 재생된_응답도_저장된_상태로_내려준다() throws Exception {
            // 재생일 때는 실제 처리를 실행하지 않아야 한다.
            given(idempotentRequestExecutor.execute(anyLong(), anyString(), anyString(), any(), any(), anyInt(), any()))
                    .willReturn(new IdempotentRequestExecutor.Outcome<>(
                            HttpStatus.OK.value(), CheckoutResponse.from(approvedPayment()), true));

            mockMvc.perform(post("/api/v1/payments/checkout")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME)
                            .header(PaymentController.IDEMPOTENCY_KEY_HEADER, "key-1")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.paymentStatus").value("SUCCESS"));

            org.mockito.Mockito.verify(paymentCheckoutService, org.mockito.Mockito.never())
                    .checkout(anyLong(), anyLong(), anyString(), anyLong());
        }
    }

    @Nested
    @DisplayName("GET /{paymentId}/receipt")
    class ReceiptTest {

        @Test
        void 영수증을_내려준다() throws Exception {
            given(paymentQueryService.getOwnedPayment(1L, 1L)).willReturn(approvedPayment());

            mockMvc.perform(get("/api/v1/payments/1/receipt")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("주문 완료 및 결제 영수증 정보가 조회되었습니다."))
                    .andExpect(jsonPath("$.data.paymentId").value(1))
                    .andExpect(jsonPath("$.data.orderId").value(111))
                    .andExpect(jsonPath("$.data.totalAmount").value(32000))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        }

        @Test
        void 조회는_토큰의_주체로_한다() throws Exception {
            // 경로나 본문으로 받은 사용자 식별자를 쓰면 타인 결제를 조회할 수 있게 된다.
            given(paymentQueryService.getOwnedPayment(1L, 1L)).willReturn(approvedPayment());

            mockMvc.perform(get("/api/v1/payments/1/receipt")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME))
                    .andExpect(status().isOk());

            org.mockito.Mockito.verify(paymentQueryService).getOwnedPayment(1L, 1L);
        }

        @Test
        void 응답_필드는_명세와_같다() throws Exception {
            given(paymentQueryService.getOwnedPayment(1L, 1L)).willReturn(approvedPayment());

            mockMvc.perform(get("/api/v1/payments/1/receipt")
                            .requestAttr(AuthenticatedPrincipal.ATTRIBUTE, ME))
                    .andExpect(jsonPath("$.data.paymentMethod").value("CARD"))
                    .andExpect(jsonPath("$.data.receiptUrl").value("https://toss.im/r/1"))
                    .andExpect(jsonPath("$.data.paymentCompletedAt").isNotEmpty());
        }

        @Test
        void ReceiptResponse는_엔티티에서_그대로_옮긴다() {
            ReceiptResponse response = ReceiptResponse.from(approvedPayment());

            org.assertj.core.api.Assertions.assertThat(response)
                    .extracting(ReceiptResponse::paymentId, ReceiptResponse::orderId,
                            ReceiptResponse::totalAmount, ReceiptResponse::paymentMethod)
                    .containsExactly(1L, 111L, 32_000L, "CARD");
        }
    }
}
