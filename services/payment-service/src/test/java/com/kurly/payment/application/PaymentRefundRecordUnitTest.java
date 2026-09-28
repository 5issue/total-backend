package com.kurly.payment.application;

import com.kurly.payment.domain.entity.Payment;
import com.kurly.payment.domain.entity.PaymentCancel;
import com.kurly.payment.domain.entity.PaymentOutbox;
import com.kurly.payment.domain.entity.PaymentRetry;
import com.kurly.payment.domain.enums.PaymentStatus;
import com.kurly.payment.domain.repository.IdempotencyKeyRepository;
import com.kurly.payment.domain.repository.PaymentCancelRepository;
import com.kurly.payment.domain.repository.PaymentOutboxRepository;
import com.kurly.payment.domain.repository.PaymentRepository;
import com.kurly.payment.domain.repository.PaymentRetryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * 부분 환불 기록(payment 추가 통신 명세).
 *
 * <p>전액 취소 경로와 <b>완료 처리가 달라야 한다.</b> 일부만 환불했는데 결제를 취소 상태로 옮기면
 * 남은 금액이 환불된 것처럼 보이고, OMS에는 완료 통보가 나가지 않아 반품이 대기에 남는다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("부분 환불 기록")
class PaymentRefundRecordUnitTest {

    private static final Long ORDER_ID = 7001L;
    private static final Long OMS_RETURN_ID = 3001L;
    private static final long TOTAL = 32_000L;

    @Mock PaymentRepository paymentRepository;
    @Mock PaymentCancelRepository paymentCancelRepository;
    @Mock PaymentOutboxRepository paymentOutboxRepository;
    @Mock PaymentRetryRepository paymentRetryRepository;
    @Mock IdempotencyKeyRepository idempotencyKeyRepository;

    PaymentRecordService paymentRecordService;

    @BeforeEach
    void createService() {
        paymentRecordService = new PaymentRecordService(paymentRepository, paymentCancelRepository,
                paymentOutboxRepository, paymentRetryRepository, idempotencyKeyRepository,
                JsonMapper.builder().build());
    }

    private Payment approvedPayment() {
        Payment payment = Payment.builder().orderId(ORDER_ID).userId(1L).totalAmount(TOTAL).build();
        ReflectionTestUtils.setField(payment, "id", 500L);
        payment.approve("TOSS-KEY", "CARD", "https://receipt");
        return payment;
    }

    private PaymentCancel refundCancel(Payment payment, long amount) {
        PaymentCancel cancel = PaymentCancel.builder()
                .payment(payment).cancelReason("OMS_RETURN_REFUND:x")
                .cancelAmount(amount).dedupKey("evt-1").omsReturnId(OMS_RETURN_ID)
                .build();
        ReflectionTestUtils.setField(cancel, "id", 900L);
        return cancel;
    }

    private void givenLockedPayment(Payment payment) {
        given(paymentRepository.findByOrderIdAndStatusForUpdate(ORDER_ID, PaymentStatus.SUCCESS))
                .willReturn(Optional.of(payment));
    }

    @Test
    void 개시는_결제_행을_잠그고_한도를_검사한다() {
        Payment payment = approvedPayment();
        givenLockedPayment(payment);
        given(paymentCancelRepository.sumUnsettledAmountByPaymentId(any())).willReturn(0L);
        given(paymentCancelRepository.save(any())).willReturn(refundCancel(payment, 12_000L));

        PaymentRecordService.RefundTicket ticket = paymentRecordService
                .beginRefund(ORDER_ID, "reason", 12_000L, "evt-1", OMS_RETURN_ID);

        assertThat(ticket.paymentKey()).isEqualTo("TOSS-KEY");
        assertThat(ticket.amount()).isEqualTo(12_000L);
        // 잠금 없는 조회를 쓰면 동시 요청이 각자 한도를 통과한다.
        verify(paymentRepository).findByOrderIdAndStatusForUpdate(ORDER_ID, PaymentStatus.SUCCESS);
    }

    @Test
    void 한도는_진행_중인_취소까지_더해서_본다() {
        // 성공한 것만 더하면 동시에 들어온 두 요청이 모두 통과해 합계가 결제 금액을 넘는다.
        givenLockedPayment(approvedPayment());
        given(paymentCancelRepository.sumUnsettledAmountByPaymentId(any())).willReturn(25_000L);

        assertThatThrownBy(() -> paymentRecordService
                .beginRefund(ORDER_ID, "reason", 10_000L, "evt-2", OMS_RETURN_ID))
                .isInstanceOf(PaymentRecordService.RefundAmountExceededException.class);
    }

    @Test
    void 부분_환불은_결제를_취소_상태로_옮기지_않는다() {
        Payment payment = approvedPayment();
        PaymentCancel cancel = refundCancel(payment, 12_000L);
        given(paymentCancelRepository.findById(900L)).willReturn(Optional.of(cancel));
        given(paymentCancelRepository.sumSucceededAmountByPaymentId(any())).willReturn(12_000L);

        paymentRecordService.completeRefund(900L, "pg-cancel-1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void 누적_환불이_결제_금액에_닿으면_취소_상태로_옮긴다() {
        Payment payment = approvedPayment();
        PaymentCancel cancel = refundCancel(payment, 20_000L);
        given(paymentCancelRepository.findById(900L)).willReturn(Optional.of(cancel));
        given(paymentCancelRepository.sumSucceededAmountByPaymentId(any())).willReturn(TOTAL);

        paymentRecordService.completeRefund(900L, "pg-cancel-1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
    }

    @Test
    void 완료_통보를_아웃박스에_적재한다() {
        Payment payment = approvedPayment();
        given(paymentCancelRepository.findById(900L)).willReturn(Optional.of(refundCancel(payment, 12_000L)));
        given(paymentCancelRepository.sumSucceededAmountByPaymentId(any())).willReturn(12_000L);

        paymentRecordService.completeRefund(900L, "pg-cancel-1");

        ArgumentCaptor<PaymentOutbox> saved = ArgumentCaptor.forClass(PaymentOutbox.class);
        verify(paymentOutboxRepository).save(saved.capture());
        // 라우팅 키는 이벤트 타입에서 만들어진다 — payment.refund.completed
        assertThat(saved.getValue().getEventType()).isEqualTo("PAYMENT_REFUND_COMPLETED");
        assertThat(saved.getValue().getPayload())
                .contains("\"omsReturnId\":3001")
                .contains("\"refundAmount\":12000");
    }

    @Test
    void 부분_환불_실패는_환불_재시도로_적재한다() {
        // 전액 취소 작업으로 적재하면 재시도 완료가 결제를 취소 상태로 옮기고
        // PAYMENT_CANCELED를 발행해, OMS는 완료 통보를 받지 못한다.
        Payment payment = approvedPayment();
        given(paymentCancelRepository.findById(900L)).willReturn(Optional.of(refundCancel(payment, 12_000L)));

        paymentRecordService.failCancel(900L, "PG 오류");

        ArgumentCaptor<PaymentRetry> saved = ArgumentCaptor.forClass(PaymentRetry.class);
        verify(paymentRetryRepository).save(saved.capture());
        assertThat(saved.getValue().getTaskType()).isEqualTo("PG_REFUND");
    }
}
