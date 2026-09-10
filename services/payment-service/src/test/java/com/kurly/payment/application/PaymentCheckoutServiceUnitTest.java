package com.kurly.payment.application;

import com.kurly.payment.application.port.OrderClient;
import com.kurly.payment.application.port.PgClient;
import com.kurly.payment.domain.entity.Payment;
import com.kurly.payment.domain.enums.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.kurly.payment.application.PaymentFixtures.AMOUNT;
import static com.kurly.payment.application.PaymentFixtures.ORDER_ID;
import static com.kurly.payment.application.PaymentFixtures.USER_ID;
import static com.kurly.payment.application.PaymentFixtures.approvedPayment;
import static com.kurly.payment.application.PaymentFixtures.payment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentCheckoutServiceUnitTest {

    private static final String PAYMENT_KEY = "TOSS-KEY";

    @Mock OrderClient orderClient;
    @Mock PgClient pgClient;
    @Mock PaymentRecordService paymentRecordService;
    @InjectMocks PaymentCheckoutService paymentCheckoutService;

    @BeforeEach
    void payableOrder() {
        given(orderClient.fetch(ORDER_ID))
                .willReturn(new OrderClient.OrderSnapshot(ORDER_ID, USER_ID, AMOUNT, true));
    }

    @Nested
    @DisplayName("승인")
    class ApproveTest {

        @Test
        void 주문_검증_후_승인하고_결과를_기록한다() {
            given(paymentRecordService.createRequested(ORDER_ID, USER_ID, AMOUNT))
                    .willReturn(payment(10L));
            given(pgClient.approve(PAYMENT_KEY, ORDER_ID, AMOUNT))
                    .willReturn(new PgClient.Approval(PAYMENT_KEY, "CARD", "https://toss.im/r/1"));
            given(paymentRecordService.recordApproval(eq(10L), any()))
                    .willReturn(approvedPayment(10L));

            Payment result = paymentCheckoutService.checkout(USER_ID, ORDER_ID, PAYMENT_KEY, AMOUNT);

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            verify(orderClient).completePayment(eq(ORDER_ID), eq(10L), eq(AMOUNT), any());
        }

        @Test
        void 승인_전에_결제_행을_먼저_남긴다() {
            // 승인 결과를 어디에 기록할지 미리 정해두지 않으면 응답을 받고도 남길 곳이 없다.
            given(paymentRecordService.createRequested(ORDER_ID, USER_ID, AMOUNT))
                    .willReturn(payment(10L));
            given(pgClient.approve(any(), any(), anyLong()))
                    .willReturn(new PgClient.Approval(PAYMENT_KEY, "CARD", "https://toss.im/r/1"));
            given(paymentRecordService.recordApproval(eq(10L), any())).willReturn(approvedPayment(10L));

            paymentCheckoutService.checkout(USER_ID, ORDER_ID, PAYMENT_KEY, AMOUNT);

            var inOrder = org.mockito.Mockito.inOrder(paymentRecordService, pgClient);
            inOrder.verify(paymentRecordService).createRequested(ORDER_ID, USER_ID, AMOUNT);
            inOrder.verify(pgClient).approve(PAYMENT_KEY, ORDER_ID, AMOUNT);
        }

        @Test
        void 금액은_주문_서비스_값을_기준으로_삼는다() {
            // 클라이언트가 보낸 금액을 그대로 승인하면 위변조를 막을 수 없다.
            given(paymentRecordService.createRequested(ORDER_ID, USER_ID, AMOUNT))
                    .willReturn(payment(10L));
            given(pgClient.approve(PAYMENT_KEY, ORDER_ID, AMOUNT))
                    .willReturn(new PgClient.Approval(PAYMENT_KEY, "CARD", "https://toss.im/r/1"));
            given(paymentRecordService.recordApproval(eq(10L), any())).willReturn(approvedPayment(10L));

            paymentCheckoutService.checkout(USER_ID, ORDER_ID, PAYMENT_KEY, AMOUNT);

            verify(pgClient).approve(PAYMENT_KEY, ORDER_ID, AMOUNT);
            verify(paymentRecordService, never()).recordFailure(anyLong());
        }
    }
}
