package com.kurly.payment.infrastructure.messaging;

import com.kurly.payment.application.OmsRefundService;
import com.kurly.payment.application.PaymentRecordService;
import com.kurly.payment.exception.InvalidPaymentStatusException;
import com.kurly.payment.exception.PaymentNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * OMS 반품 환불 요청 소비(payment 추가 통신 명세).
 *
 * <p>재시도해도 결과가 같은 오류는 즉시 DLQ로 보낸다. 환불이 막히면 다른 고객의 환불까지 밀린다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OMS 반품 환불 요청 소비")
class OmsRefundEventListenerUnitTest {

    @Mock OmsRefundService omsRefundService;

    @InjectMocks OmsRefundEventListener listener;

    private OmsRefundRequestedEvent event(UUID eventId, Long omsReturnId, Long orderId, Long refundAmount) {
        return new OmsRefundRequestedEvent(eventId, 9001L, omsReturnId, orderId,
                refundAmount, 0L, List.of(1L), LocalDateTime.now());
    }

    private OmsRefundRequestedEvent valid() {
        return event(UUID.randomUUID(), 3001L, 7001L, 12_000L);
    }

    @Test
    void 정상_이벤트는_환불로_넘긴다() {
        assertThatCode(() -> listener.onRefundRequested(valid())).doesNotThrowAnyException();
        verify(omsRefundService).refund(any());
    }

    @Test
    void omsReturnId가_없으면_환불하지_않고_DLQ로_보낸다() {
        // OMS가 완료 통보로 반품 건을 찾는 조회 키다. 없으면 돈은 나가고 상태는 어긋난다.
        assertThatThrownBy(() -> listener.onRefundRequested(event(UUID.randomUUID(), null, 7001L, 12_000L)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(omsRefundService, never()).refund(any());
    }

    @Test
    void 필수값이_없으면_DLQ로_보낸다() {
        assertThatThrownBy(() -> listener.onRefundRequested(null))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> listener.onRefundRequested(event(null, 3001L, 7001L, 12_000L)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> listener.onRefundRequested(event(UUID.randomUUID(), 3001L, null, 12_000L)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> listener.onRefundRequested(event(UUID.randomUUID(), 3001L, 7001L, 0L)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(omsRefundService, never()).refund(any());
    }

    @Test
    void 대상_결제가_없으면_DLQ로_보낸다() {
        // 재배달해도 결과가 같다. 발행자 버그이므로 사람이 봐야 한다.
        willThrow(new PaymentNotFoundException()).given(omsRefundService).refund(any());

        assertThatThrownBy(() -> listener.onRefundRequested(valid()))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void 결제_금액_초과_요청은_DLQ로_보낸다() {
        willThrow(new PaymentRecordService.RefundAmountExceededException("초과"))
                .given(omsRefundService).refund(any());

        assertThatThrownBy(() -> listener.onRefundRequested(valid()))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void 취소할_수_없는_상태면_넘어간다() {
        // 중복 배달이거나 다른 경로에서 먼저 취소된 경우다. 재시도할 일이 아니다.
        willThrow(new InvalidPaymentStatusException()).given(omsRefundService).refund(any());

        assertThatCode(() -> listener.onRefundRequested(valid())).doesNotThrowAnyException();
    }

    @Test
    void PG_오류는_그대로_올려_재배달시킨다() {
        willThrow(new RuntimeException("PG 오류")).given(omsRefundService).refund(any());

        assertThatThrownBy(() -> listener.onRefundRequested(valid()))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
}
