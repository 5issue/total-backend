package com.kurly.payment.application;

import com.kurly.payment.application.port.PgClient;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
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
 *
 * <p>개시·PG·기록 세 단계의 <b>실패 처리가 서로 달라야 한다.</b> 특히 PG가 환불한 뒤 기록이
 * 실패했을 때 실패로 확정하면, 재시도가 환불되지 않은 것처럼 다루게 된다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OMS 반품 환불")
class OmsRefundServiceUnitTest {

    private static final Long ORDER_ID = 7001L;
    private static final Long OMS_RETURN_ID = 3001L;
    private static final Long CANCEL_ID = 900L;

    @Mock PaymentRecordService paymentRecordService;
    @Mock PgClient pgClient;

    @InjectMocks OmsRefundService omsRefundService;

    private OmsRefundRequestedEvent event(long refundAmount) {
        return new OmsRefundRequestedEvent(UUID.randomUUID(), 9001L, OMS_RETURN_ID, ORDER_ID,
                refundAmount, 0L, List.of(1L, 2L), LocalDateTime.now());
    }

    private void givenBeginSucceeds(long amount) {
        given(paymentRecordService.beginRefund(any(), anyString(), anyLong(), anyString(), any()))
                .willReturn(new PaymentRecordService.RefundTicket(CANCEL_ID, "TOSS-KEY", amount));
    }

    private void givenPgSucceeds() {
        given(pgClient.cancel(anyString(), anyLong(), anyString(), any()))
                .willReturn(new PgClient.Cancellation("pg-cancel-1"));
    }

    @Test
    void 요청_금액만큼_PG_취소를_호출한다() {
        givenBeginSucceeds(12_000L);
        givenPgSucceeds();

        assertThat(omsRefundService.refund(event(12_000L))).isTrue();

        // 결제 총액이 아니라 요청 금액으로 취소해야 한다. 전액으로 부르면 반품하지 않은 상품까지 환불된다.
        verify(pgClient).cancel(eq("TOSS-KEY"), eq(12_000L), anyString(), eq(CANCEL_ID));
        verify(paymentRecordService).completeRefund(CANCEL_ID, "pg-cancel-1");
    }

    @Test
    void 개시에_omsReturnId와_중복키를_함께_넘긴다() {
        givenBeginSucceeds(12_000L);
        givenPgSucceeds();
        OmsRefundRequestedEvent event = event(12_000L);

        omsRefundService.refund(event);

        // 중복키는 이벤트 식별자다. omsReturnId는 완료 통보에 되돌려주기 위해 취소 행에 남는다.
        verify(paymentRecordService).beginRefund(eq(ORDER_ID), anyString(), eq(12_000L),
                eq(event.eventId().toString()), eq(OMS_RETURN_ID));
    }

    @Test
    void 유니크_제약_위반은_중복_배달로_다룬다() {
        // 조회 후 삽입으로는 동시 재배달을 막을 수 없다. 제약 위반이 유일한 경합 차단점이다.
        willThrow(new DataIntegrityViolationException("uk_payment_cancels_dedup_key"))
                .given(paymentRecordService).beginRefund(any(), anyString(), anyLong(), anyString(), any());

        assertThat(omsRefundService.refund(event(12_000L))).isFalse();

        verify(pgClient, never()).cancel(anyString(), anyLong(), anyString(), any());
    }

    @Test
    void 금액_초과는_개시_단계에서_막힌다() {
        willThrow(new PaymentRecordService.RefundAmountExceededException("초과"))
                .given(paymentRecordService).beginRefund(any(), anyString(), anyLong(), anyString(), any());

        assertThatThrownBy(() -> omsRefundService.refund(event(99_000L)))
                .isInstanceOf(PaymentRecordService.RefundAmountExceededException.class);

        verify(pgClient, never()).cancel(anyString(), anyLong(), anyString(), any());
    }

    @Test
    void 성공한_결제가_없으면_거부한다() {
        willThrow(new PaymentNotFoundException())
                .given(paymentRecordService).beginRefund(any(), anyString(), anyLong(), anyString(), any());

        assertThatThrownBy(() -> omsRefundService.refund(event(12_000L)))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void PG_실패는_이력에_남기고_다시_던진다() {
        // 여기서 조용히 끝내면 고객 돈이 묶인 채 잊힌다. 재시도 배치가 이어받아야 한다.
        givenBeginSucceeds(12_000L);
        willThrow(new RuntimeException("PG 오류")).given(pgClient)
                .cancel(anyString(), anyLong(), anyString(), any());

        assertThatThrownBy(() -> omsRefundService.refund(event(12_000L)))
                .isInstanceOf(RuntimeException.class);

        verify(paymentRecordService).failCancel(eq(CANCEL_ID), anyString());
        verify(paymentRecordService, never()).completeRefund(any(), anyString());
    }

    @Test
    void 환불_후_기록_실패는_실패로_확정하지_않는다() {
        // PG는 이미 환불했다. 실패로 확정하면 재시도가 "환불되지 않았다"로 다루고,
        // 전액 취소 완료 처리를 타 결제 상태와 OMS 상태가 모두 어긋난다.
        givenBeginSucceeds(12_000L);
        givenPgSucceeds();
        willThrow(new RuntimeException("DB 오류")).given(paymentRecordService)
                .completeRefund(any(), anyString());

        assertThatThrownBy(() -> omsRefundService.refund(event(12_000L)))
                .isInstanceOf(RuntimeException.class);

        verify(paymentRecordService, never()).failCancel(any(), anyString());
    }
}
