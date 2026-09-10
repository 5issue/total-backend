package com.kurly.payment.application;

import com.kurly.payment.application.port.OrderClient;
import com.kurly.payment.application.port.PgClient;
import com.kurly.payment.exception.AmountMismatchException;
import com.kurly.payment.exception.DuplicatePaymentRequestException;
import com.kurly.payment.exception.InvalidPaymentStatusException;
import com.kurly.payment.exception.OrderNotFoundException;
import com.kurly.payment.exception.PaymentDeclinedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static com.kurly.payment.application.PaymentFixtures.AMOUNT;
import static com.kurly.payment.application.PaymentFixtures.ORDER_ID;
import static com.kurly.payment.application.PaymentFixtures.USER_ID;
import static com.kurly.payment.application.PaymentFixtures.approvedPayment;
import static com.kurly.payment.application.PaymentFixtures.cancel;
import static com.kurly.payment.application.PaymentFixtures.payment;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentCheckoutServiceUnitExceptionTest {

    private static final String PAYMENT_KEY = "TOSS-KEY";

    @Mock OrderClient orderClient;
    @Mock PgClient pgClient;
    @Mock PaymentRecordService paymentRecordService;
    @InjectMocks PaymentCheckoutService paymentCheckoutService;

    private void givenOrder(Long owner, long amount, boolean payable) {
        given(orderClient.fetch(ORDER_ID))
                .willReturn(new OrderClient.OrderSnapshot(ORDER_ID, owner, amount, payable));
    }

    @Nested
    @DisplayName("주문 검증 실패")
    class OrderVerificationTest {

        @Test
        void 타인의_주문이면_404다() {
            // 주문의 존재 여부를 구분해 노출하지 않는다.
            givenOrder(999L, AMOUNT, true);

            assertThatThrownBy(() -> paymentCheckoutService.checkout(USER_ID, ORDER_ID, PAYMENT_KEY, AMOUNT))
                    .isInstanceOf(OrderNotFoundException.class);
            verify(pgClient, never()).approve(any(), any(), anyLong());
        }

        @Test
        void 금액이_다르면_전용_예외를_던진다() {
            givenOrder(USER_ID, 50_000L, true);

            assertThatThrownBy(() -> paymentCheckoutService.checkout(USER_ID, ORDER_ID, PAYMENT_KEY, AMOUNT))
                    .isInstanceOf(AmountMismatchException.class);
        }

        @Test
        void 결제할_수_없는_상태면_거부한다() {
            givenOrder(USER_ID, AMOUNT, false);

            assertThatThrownBy(() -> paymentCheckoutService.checkout(USER_ID, ORDER_ID, PAYMENT_KEY, AMOUNT))
                    .isInstanceOf(InvalidPaymentStatusException.class);
        }

        @Test
        void 검증에_실패하면_결제_행을_만들지_않는다() {
            givenOrder(USER_ID, 50_000L, true);

            assertThatThrownBy(() -> paymentCheckoutService.checkout(USER_ID, ORDER_ID, PAYMENT_KEY, AMOUNT))
                    .isInstanceOf(AmountMismatchException.class);
            verify(paymentRecordService, never()).createRequested(any(), any(), anyLong());
        }
    }

    @Nested
    @DisplayName("PG 승인 실패")
    class ApprovalFailureTest {

        @Test
        void 실패를_결제_행에_남기고_예외를_전파한다() {
            // 흔적이 없으면 고객 문의에 답할 수 없다.
            givenOrder(USER_ID, AMOUNT, true);
            given(paymentRecordService.createRequested(ORDER_ID, USER_ID, AMOUNT)).willReturn(payment(10L));
            willThrow(new PaymentDeclinedException()).given(pgClient).approve(PAYMENT_KEY, ORDER_ID, AMOUNT);

            assertThatThrownBy(() -> paymentCheckoutService.checkout(USER_ID, ORDER_ID, PAYMENT_KEY, AMOUNT))
                    .isInstanceOf(PaymentDeclinedException.class);
            verify(paymentRecordService).recordFailure(10L);
        }
    }

    @Nested
    @DisplayName("주문 중복 결제")
    class DuplicatePaymentTest {

        @Test
        void 유니크_제약에_걸리면_방금_승인분을_되돌린다() {
            // 동시 요청이 검증을 함께 통과해도 DB가 두 번째 성공을 막는다. 다만 그 시점엔 PG 승인이
            // 이미 끝났으므로, 되돌리지 않으면 고객이 두 번 결제된 채로 남는다.
            givenOrder(USER_ID, AMOUNT, true);
            given(paymentRecordService.createRequested(ORDER_ID, USER_ID, AMOUNT)).willReturn(payment(10L));
            given(pgClient.approve(PAYMENT_KEY, ORDER_ID, AMOUNT))
                    .willReturn(new PgClient.Approval(PAYMENT_KEY, "CARD", "https://toss.im/r/1"));
            willThrow(new DataIntegrityViolationException("duplicate success_order_id"))
                    .given(paymentRecordService).recordApproval(eq(10L), any());
            given(paymentRecordService.beginCancel(eq(10L), anyString(), eq(AMOUNT)))
                    .willReturn(cancel(20L, approvedPayment(10L), AMOUNT));
            given(pgClient.cancel(anyString(), eq(AMOUNT), anyString()))
                    .willReturn(new PgClient.Cancellation("PG-CANCEL-DUP"));

            assertThatThrownBy(() -> paymentCheckoutService.checkout(USER_ID, ORDER_ID, PAYMENT_KEY, AMOUNT))
                    .isInstanceOf(DuplicatePaymentRequestException.class);

            verify(paymentRecordService).completeCancel(20L, "PG-CANCEL-DUP");
        }

        @Test
        void 되돌릴_때는_PG가_준_키를_쓴다() {
            // 기록이 롤백돼 엔티티에는 키가 남아 있지 않다. 승인 응답의 키만이 취소에 쓸 수 있다.
            givenOrder(USER_ID, AMOUNT, true);
            given(paymentRecordService.createRequested(ORDER_ID, USER_ID, AMOUNT)).willReturn(payment(10L));
            given(pgClient.approve(PAYMENT_KEY, ORDER_ID, AMOUNT))
                    .willReturn(new PgClient.Approval("PG-ISSUED-KEY", "CARD", "https://toss.im/r/1"));
            willThrow(new DataIntegrityViolationException("duplicate"))
                    .given(paymentRecordService).recordApproval(eq(10L), any());
            given(paymentRecordService.beginCancel(eq(10L), anyString(), eq(AMOUNT)))
                    .willReturn(cancel(20L, approvedPayment(10L), AMOUNT));
            given(pgClient.cancel(anyString(), eq(AMOUNT), anyString()))
                    .willReturn(new PgClient.Cancellation("PG-CANCEL-DUP"));

            assertThatThrownBy(() -> paymentCheckoutService.checkout(USER_ID, ORDER_ID, PAYMENT_KEY, AMOUNT))
                    .isInstanceOf(DuplicatePaymentRequestException.class);

            verify(pgClient).cancel(eq("PG-ISSUED-KEY"), eq(AMOUNT), anyString());
        }
    }

    @Nested
    @DisplayName("주문 만료 보상 취소")
    class CompensationTest {

        @Test
        void 주문이_만료됐으면_즉시_취소한다() {
            // 결제만 승인되고 주문은 만료된 상태를 두면 고객 돈만 빠져나간다.
            givenOrder(USER_ID, AMOUNT, true);
            given(paymentRecordService.createRequested(ORDER_ID, USER_ID, AMOUNT)).willReturn(payment(10L));
            given(pgClient.approve(PAYMENT_KEY, ORDER_ID, AMOUNT))
                    .willReturn(new PgClient.Approval(PAYMENT_KEY, "CARD", "https://toss.im/r/1"));
            given(paymentRecordService.recordApproval(eq(10L), any())).willReturn(approvedPayment(10L));
            willThrow(new OrderClient.OrderAlreadyExpiredException(ORDER_ID))
                    .given(orderClient).completePayment(ORDER_ID);
            given(paymentRecordService.beginCancel(eq(10L), anyString(), eq(AMOUNT)))
                    .willReturn(cancel(20L, approvedPayment(10L), AMOUNT));
            given(pgClient.cancel(anyString(), eq(AMOUNT), anyString()))
                    .willReturn(new PgClient.Cancellation("PG-CANCEL-1"));

            assertThatThrownBy(() -> paymentCheckoutService.checkout(USER_ID, ORDER_ID, PAYMENT_KEY, AMOUNT))
                    .isInstanceOf(InvalidPaymentStatusException.class);
            verify(paymentRecordService).completeCancel(20L, "PG-CANCEL-1");
        }

        @Test
        void 보상_취소마저_실패하면_재시도_큐로_넘긴다() {
            // 여기서 멈추면 고객 돈이 묶인 채로 잊힌다.
            givenOrder(USER_ID, AMOUNT, true);
            given(paymentRecordService.createRequested(ORDER_ID, USER_ID, AMOUNT)).willReturn(payment(10L));
            given(pgClient.approve(PAYMENT_KEY, ORDER_ID, AMOUNT))
                    .willReturn(new PgClient.Approval(PAYMENT_KEY, "CARD", "https://toss.im/r/1"));
            given(paymentRecordService.recordApproval(eq(10L), any())).willReturn(approvedPayment(10L));
            willThrow(new OrderClient.OrderAlreadyExpiredException(ORDER_ID))
                    .given(orderClient).completePayment(ORDER_ID);
            given(paymentRecordService.beginCancel(eq(10L), anyString(), eq(AMOUNT)))
                    .willReturn(cancel(20L, approvedPayment(10L), AMOUNT));
            willThrow(new IllegalStateException("PG timeout"))
                    .given(pgClient).cancel(anyString(), eq(AMOUNT), anyString());

            assertThatThrownBy(() -> paymentCheckoutService.checkout(USER_ID, ORDER_ID, PAYMENT_KEY, AMOUNT))
                    .isInstanceOf(InvalidPaymentStatusException.class);
            verify(paymentRecordService).failCancel(eq(20L), anyString());
        }
    }
}
