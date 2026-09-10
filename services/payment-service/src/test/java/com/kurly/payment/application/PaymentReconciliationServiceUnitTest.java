package com.kurly.payment.application;

import com.kurly.payment.application.port.OrderClient;
import com.kurly.payment.application.port.PgClient;
import com.kurly.payment.domain.enums.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.kurly.payment.application.PaymentFixtures.AMOUNT;
import static com.kurly.payment.application.PaymentFixtures.ORDER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationServiceUnitTest {

    private static final Long PAYMENT_ID = 10L;
    private static final Duration STALE_AFTER = Duration.ofMinutes(10);

    @Mock PgClient pgClient;
    @Mock OrderClient orderClient;
    @Mock PaymentRecordService paymentRecordService;
    @Mock PaymentCompensationService paymentCompensationService;
    @InjectMocks PaymentReconciliationService paymentReconciliationService;

    private void givenTarget(PaymentStatus status) {
        given(paymentRecordService.claimReconcilable(anyInt(), any(), any()))
                .willReturn(List.of(new PaymentRecordService.ReconcileTarget(
                        PAYMENT_ID, ORDER_ID, AMOUNT, status, "TOSS-KEY", false)));
    }

    private void givenPgSays(String status, boolean approved) {
        givenPgSays(status, approved, false);
    }

    private void givenPgSays(String status, boolean approved, boolean pending) {
        given(pgClient.findByOrderId(ORDER_ID)).willReturn(Optional.of(new PgClient.Inquiry(
                "TOSS-KEY", status, "CARD", "https://toss.im/r/1", approved, pending)));
    }

    private void reconcile() {
        paymentReconciliationService.reconcileStalePayments(100, STALE_AFTER);
    }

    @Nested
    @DisplayName("대상 선정")
    class ClaimTest {

        @Test
        void 유예_시간을_뺀_시각을_기준으로_대상을_찾는다() {
            // 진행 중인 정상 결제까지 집으면 승인과 대사가 같은 결제를 두고 경합한다.
            given(paymentRecordService.claimReconcilable(anyInt(), any(), any())).willReturn(List.of());
            LocalDateTime before = LocalDateTime.now().minus(STALE_AFTER);

            reconcile();

            ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(paymentRecordService).claimReconcilable(eq(100), captor.capture(), any());
            assertThat(captor.getValue()).isAfterOrEqualTo(before);
        }

        @Test
        void 대상이_없으면_PG를_호출하지_않는다() {
            given(paymentRecordService.claimReconcilable(anyInt(), any(), any())).willReturn(List.of());

            reconcile();

            verify(pgClient, never()).findByOrderId(anyLong());
        }
    }

    @Nested
    @DisplayName("PG가 승인하지 않은 건")
    class NotApprovedTest {

        @Test
        void 결제가_아예_없으면_실패로_확정한다() {
            givenTarget(PaymentStatus.REQUESTED);
            given(pgClient.findByOrderId(ORDER_ID)).willReturn(Optional.empty());

            reconcile();

            verify(paymentRecordService).recordFailure(PAYMENT_ID);
        }

        @Test
        void 승인_실패_상태면_실패로_확정한다() {
            givenTarget(PaymentStatus.REQUESTED);
            givenPgSays("ABORTED", false);

            reconcile();

            verify(paymentRecordService).recordFailure(PAYMENT_ID);
        }

        @Test
        void 아직_진행_중이면_실패로_못박지_않는다() {
            // READY·IN_PROGRESS는 결론이 안 난 상태다. 실패로 확정하면 결제될 수 있었던 건을 죽인다.
            givenTarget(PaymentStatus.REQUESTED);
            givenPgSays("IN_PROGRESS", false, true);

            reconcile();

            verify(paymentRecordService, never()).recordFailure(anyLong());
            verify(paymentRecordService, never()).recordApproval(anyLong(), any());
        }

        @Test
        void 아직_진행_중이면_선점을_풀어_다음_주기에_다시_본다() {
            givenTarget(PaymentStatus.REQUESTED);
            givenPgSays("WAITING_FOR_DEPOSIT", false, true);

            reconcile();

            verify(paymentRecordService).releaseReconciliationClaim(PAYMENT_ID);
        }

        @Test
        void 이미_실패로_기록된_건은_다시_쓰지_않는다() {
            givenTarget(PaymentStatus.FAILED);
            givenPgSays("ABORTED", false);

            reconcile();

            verify(paymentRecordService, never()).recordFailure(anyLong());
        }
    }

    @Nested
    @DisplayName("PG가 승인한 건")
    class ApprovedTest {

        @Test
        void 기록을_승인으로_정정한다() {
            givenTarget(PaymentStatus.REQUESTED);
            givenPgSays("DONE", true);

            reconcile();

            ArgumentCaptor<PgClient.Approval> captor = ArgumentCaptor.forClass(PgClient.Approval.class);
            verify(paymentRecordService).recordApproval(eq(PAYMENT_ID), captor.capture());
            assertThat(captor.getValue())
                    .extracting(PgClient.Approval::paymentKey, PgClient.Approval::method)
                    .containsExactly("TOSS-KEY", "CARD");
        }

        @Test
        void 타임아웃을_실패로_기록했던_건도_승인으로_정정한다() {
            // 대사의 존재 이유다. 이 경로가 없으면 고객 돈만 빠져나간 채로 영영 묻힌다.
            givenTarget(PaymentStatus.FAILED);
            givenPgSays("DONE", true);

            reconcile();

            verify(paymentRecordService).recordApproval(eq(PAYMENT_ID), any());
        }

        @Test
        void 정정한_결제를_주문에_넘긴다() {
            givenTarget(PaymentStatus.REQUESTED);
            givenPgSays("DONE", true);

            reconcile();

            verify(orderClient).completePayment(eq(ORDER_ID), eq(PAYMENT_ID), eq(AMOUNT), any());
            verify(paymentCompensationService, never())
                    .compensate(anyLong(), anyString(), anyLong(), anyString());
        }

        @Test
        void 주문이_만료됐으면_환불한다() {
            // 대사 시점이면 결제 유효시간(5분)은 이미 지났을 가능성이 높다.
            givenTarget(PaymentStatus.REQUESTED);
            givenPgSays("DONE", true);
            willThrow(new OrderClient.OrderAlreadyExpiredException(ORDER_ID))
                    .given(orderClient).completePayment(eq(ORDER_ID), anyLong(), anyLong(), any());

            reconcile();

            verify(paymentCompensationService)
                    .compensate(eq(PAYMENT_ID), eq("TOSS-KEY"), eq(AMOUNT), anyString());
        }

        @Test
        void 주문_통보가_어떤_이유로든_실패하면_환불한다() {
            // 넘기지도 못한 돈을 계속 들고 있는 것이 더 나쁘다.
            givenTarget(PaymentStatus.REQUESTED);
            givenPgSays("DONE", true);
            willThrow(new IllegalStateException("order-service 응답 없음"))
                    .given(orderClient).completePayment(eq(ORDER_ID), anyLong(), anyLong(), any());

            reconcile();

            verify(paymentCompensationService)
                    .compensate(eq(PAYMENT_ID), eq("TOSS-KEY"), eq(AMOUNT), anyString());
        }
    }

    @Nested
    @DisplayName("승인됐으나 주문에 인계되지 않은 건")
    class NotHandedOverTest {

        private void givenNotHandedOver() {
            given(paymentRecordService.claimReconcilable(anyInt(), any(), any()))
                    .willReturn(List.of(new PaymentRecordService.ReconcileTarget(
                            PAYMENT_ID, ORDER_ID, AMOUNT, PaymentStatus.SUCCESS, "TOSS-KEY", true)));
        }

        @Test
        void PG에_다시_묻지_않는다() {
            // 승인은 이미 기록돼 있다. 물어볼 것이 없고 PG 호출만 낭비된다.
            givenNotHandedOver();

            reconcile();

            verify(pgClient, never()).findByOrderId(anyLong());
        }

        @Test
        void 주문_인계만_다시_시도한다() {
            givenNotHandedOver();

            reconcile();

            verify(orderClient).completePayment(eq(ORDER_ID), eq(PAYMENT_ID), eq(AMOUNT), any());
            verify(paymentRecordService).markOrderNotified(PAYMENT_ID);
        }

        @Test
        void 주문이_받지_않으면_환불한다() {
            givenNotHandedOver();
            willThrow(new OrderClient.OrderAlreadyExpiredException(ORDER_ID))
                    .given(orderClient).completePayment(eq(ORDER_ID), anyLong(), anyLong(), any());

            reconcile();

            verify(paymentCompensationService)
                    .compensate(eq(PAYMENT_ID), eq("TOSS-KEY"), eq(AMOUNT), anyString());
        }
    }

    @Nested
    @DisplayName("대사 완료 표시")
    class CompletionTest {

        @Test
        void 결론이_나면_완료로_표시한다() {
            givenTarget(PaymentStatus.REQUESTED);
            givenPgSays("ABORTED", false);

            reconcile();

            verify(paymentRecordService).completeReconciliation(PAYMENT_ID);
        }

        @Test
        void 아직_진행_중이면_완료로_표시하지_않는다() {
            // 완료로 찍으면 결론이 안 난 결제가 영영 대사 대상에서 빠진다.
            givenTarget(PaymentStatus.REQUESTED);
            givenPgSays("IN_PROGRESS", false, true);

            reconcile();

            verify(paymentRecordService, never()).completeReconciliation(anyLong());
        }

        @Test
        void 대사가_실패하면_완료로_표시하지_않고_선점만_푼다() {
            givenTarget(PaymentStatus.REQUESTED);
            willThrow(new IllegalStateException("PG timeout")).given(pgClient).findByOrderId(ORDER_ID);

            reconcile();

            verify(paymentRecordService).releaseReconciliationClaim(PAYMENT_ID);
            verify(paymentRecordService, never()).completeReconciliation(anyLong());
        }
    }

    @Nested
    @DisplayName("매달린 취소 회수")
    class StaleCancelTest {

        @Test
        void 유예를_뺀_시각을_기준으로_회수한다() {
            given(paymentRecordService.recoverStaleRequestedCancels(anyInt(), any())).willReturn(List.of(7L));
            LocalDateTime before = LocalDateTime.now().minus(Duration.ofMinutes(15));

            paymentReconciliationService.recoverStaleCancels(100, Duration.ofMinutes(15));

            ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(paymentRecordService).recoverStaleRequestedCancels(eq(100), captor.capture());
            assertThat(captor.getValue()).isAfterOrEqualTo(before);
        }
    }

    @Nested
    @DisplayName("대사 실패")
    class FailureTest {

        @Test
        void PG_조회가_실패하면_선점을_되돌린다() {
            // 되돌리지 않으면 대사되지 않은 건이 대사 완료로 남아 영영 다시 보지 않게 된다.
            givenTarget(PaymentStatus.REQUESTED);
            willThrow(new IllegalStateException("PG timeout")).given(pgClient).findByOrderId(ORDER_ID);

            reconcile();

            verify(paymentRecordService).releaseReconciliationClaim(PAYMENT_ID);
        }

        @Test
        void 한_건이_실패해도_나머지를_계속_대사한다() {
            given(paymentRecordService.claimReconcilable(anyInt(), any(), any())).willReturn(List.of(
                    new PaymentRecordService.ReconcileTarget(
                            PAYMENT_ID, ORDER_ID, AMOUNT, PaymentStatus.REQUESTED, "TOSS-KEY", false),
                    new PaymentRecordService.ReconcileTarget(
                            11L, 222L, AMOUNT, PaymentStatus.REQUESTED, "TOSS-KEY", false)));
            willThrow(new IllegalStateException("PG timeout")).given(pgClient).findByOrderId(ORDER_ID);
            given(pgClient.findByOrderId(222L)).willReturn(Optional.empty());

            reconcile();

            verify(paymentRecordService).recordFailure(11L);
        }
    }
}
