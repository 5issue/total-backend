package com.kurly.payment.application;

import com.kurly.payment.application.port.PgClient;
import com.kurly.payment.domain.entity.Payment;
import com.kurly.payment.domain.entity.PaymentCancel;
import com.kurly.payment.domain.enums.PaymentStatus;
import com.kurly.payment.domain.repository.PaymentCancelRepository;
import com.kurly.payment.domain.repository.PaymentRepository;
import com.kurly.payment.exception.PaymentNotFoundException;
import com.kurly.payment.infrastructure.messaging.OmsRefundRequestedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * OMS 반품 환불 처리(payment 추가 통신 명세).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OMS 반품 환불")
class OmsRefundServiceUnitTest {

    private static final Long ORDER_ID = 7001L;
    private static final Long OMS_RETURN_ID = 3001L;

    @Mock PaymentRepository paymentRepository;
    @Mock PaymentCancelRepository paymentCancelRepository;
    @Mock PaymentRecordService paymentRecordService;
    @Mock PgClient pgClient;

    @InjectMocks OmsRefundService omsRefundService;

    private OmsRefundRequestedEvent event(long refundAmount) {
        return new OmsRefundRequestedEvent(UUID.randomUUID(), 9001L, OMS_RETURN_ID, ORDER_ID,
                refundAmount, 0L, List.of(1L, 2L), LocalDateTime.now());
    }

    /** 같은 패키지의 {@code PaymentFixtures}를 쓴다. id는 DB가 채우므로 리플렉션으로 심는 조립이다. */
    private Payment approvedPayment() {
        return PaymentFixtures.approvedPayment(500L);
    }

    private void givenPayment(Payment payment) {
        given(paymentRepository.findByOrderIdAndStatus(ORDER_ID, PaymentStatus.SUCCESS))
                .willReturn(Optional.of(payment));
    }

    private void givenCancelBegins() {
        PaymentCancel cancel = PaymentFixtures.cancel(900L, approvedPayment(), 12_000L);
        given(paymentRecordService.beginCancel(any(), anyString(), anyLong())).willReturn(cancel);
        given(pgClient.cancel(anyString(), anyLong(), anyString(), any()))
                .willReturn(new PgClient.Cancellation("pg-cancel-1"));
    }

    @Test
    void 요청_금액만큼_PG_취소를_호출한다() {
        givenPayment(approvedPayment());
        givenCancelBegins();

        boolean refunded = omsRefundService.refund(event(12_000L));

        assertThat(refunded).isTrue();
        // 결제 총액이 아니라 요청 금액으로 취소해야 한다. 전액으로 부르면 반품하지 않은 상품까지 환불된다.
        verify(pgClient).cancel(eq("TOSS-KEY"), eq(12_000L), anyString(), any());
    }

    @Test
    void 완료_통보에_받은_omsReturnId를_그대로_싣는다() {
        givenPayment(approvedPayment());
        givenCancelBegins();

        omsRefundService.refund(event(12_000L));

        // OMS가 이 값으로 반품 건을 찾는다. 우리가 다시 계산할 수 있는 값이 아니다.
        verify(paymentRecordService).completeRefund(any(), eq("pg-cancel-1"), eq(OMS_RETURN_ID));
    }

    @Test
    void 이미_처리한_이벤트는_다시_환불하지_않는다() {
        // 브로커가 최소 1회 배달을 보장하므로 같은 요청이 두 번 온다. 두 번 환불하면 과다 지급이다.
        given(paymentCancelRepository.existsByCancelReason(anyString())).willReturn(true);

        boolean refunded = omsRefundService.refund(event(12_000L));

        assertThat(refunded).isFalse();
        verify(pgClient, never()).cancel(anyString(), anyLong(), anyString(), any());
        verify(paymentRecordService, never()).beginCancel(any(), anyString(), anyLong());
    }

    @Test
    void 결제_금액을_넘는_환불은_거부한다() {
        givenPayment(approvedPayment());
        given(paymentCancelRepository.sumSucceededAmountByPaymentId(any())).willReturn(25_000L);  // 남은 한도 7,000원

        assertThatThrownBy(() -> omsRefundService.refund(event(10_000L)))
                .isInstanceOf(OmsRefundService.RefundAmountExceededException.class);

        verify(pgClient, never()).cancel(anyString(), anyLong(), anyString(), any());
    }

    @Test
    void 성공한_결제가_없으면_거부한다() {
        given(paymentRepository.findByOrderIdAndStatus(ORDER_ID, PaymentStatus.SUCCESS))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> omsRefundService.refund(event(12_000L)))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void PG_실패는_이력에_남기고_다시_던진다() {
        // 여기서 조용히 끝내면 고객 돈이 묶인 채 잊힌다.
        givenPayment(approvedPayment());
        PaymentCancel cancel = PaymentFixtures.cancel(900L, approvedPayment(), 12_000L);
        given(paymentRecordService.beginCancel(any(), anyString(), anyLong())).willReturn(cancel);
        willThrow(new RuntimeException("PG 오류")).given(pgClient)
                .cancel(anyString(), anyLong(), anyString(), any());

        assertThatThrownBy(() -> omsRefundService.refund(event(12_000L)))
                .isInstanceOf(RuntimeException.class);

        verify(paymentRecordService).failCancel(any(), anyString());
        verify(paymentRecordService, never()).completeRefund(any(), anyString(), any());
    }
}
