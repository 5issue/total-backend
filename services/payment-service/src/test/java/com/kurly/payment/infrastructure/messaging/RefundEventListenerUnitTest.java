package com.kurly.payment.infrastructure.messaging;

import com.kurly.payment.application.PaymentCancelService;
import com.kurly.payment.exception.InvalidPaymentStatusException;
import com.kurly.payment.exception.PaymentNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefundEventListenerUnitTest {

    @Mock PaymentCancelService paymentCancelService;

    RefundEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new RefundEventListener(paymentCancelService);
    }

    /** null 값을 담을 수 있어야 해서 Map.of가 아니라 HashMap을 쓴다. */
    private static Map<String, Object> event(Object paymentId, Object userId, Object reason) {
        Map<String, Object> event = new HashMap<>();
        event.put("paymentId", paymentId);
        event.put("userId", userId);
        event.put("reason", reason);
        return event;
    }

    @Nested
    @DisplayName("환불 처리")
    class RefundTest {

        @Test
        void 메시지의_식별자로_취소한다() {
            listener.onRefundApproved(event(10, 1, "RETURN_APPROVED"));

            verify(paymentCancelService).cancel(10L, 1L, "RETURN_APPROVED");
        }

        @Test
        void 사유가_없으면_기본값을_쓴다() {
            listener.onRefundApproved(event(10, 1, null));

            verify(paymentCancelService).cancel(10L, 1L, "RETURN_APPROVED");
        }

        @Test
        void 소유자가_없으면_대조를_건너뛴다() {
            // 관리자 승인 환불에는 대조할 사용자 개념이 없는 흐름도 있다.
            listener.onRefundApproved(event(10, null, null));

            verify(paymentCancelService).cancel(eq(10L), eq(null), anyString());
        }
    }

    @Nested
    @DisplayName("중복 배달")
    class DuplicateTest {

        @Test
        void 이미_취소된_결제는_성공으로_본다() {
            // 발행이 최소 1회를 보장하므로 같은 이벤트가 두 번 오는 것은 정상이다.
            willThrow(new InvalidPaymentStatusException())
                    .given(paymentCancelService).cancel(anyLong(), any(), anyString());

            assertThatCode(() -> listener.onRefundApproved(event(10, 1, null)))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("DLQ로 보내는 경우")
    class DeadLetterTest {

        @Test
        void 대상이_맞지_않으면_재배달하지_않고_DLQ로_보낸다() {
            // 정상 상황에서는 일어나지 않는다. 발행자 버그이므로 사람이 봐야 한다.
            willThrow(new PaymentNotFoundException())
                    .given(paymentCancelService).cancel(anyLong(), any(), anyString());

            assertThatThrownBy(() -> listener.onRefundApproved(event(10, 999, null)))
                    .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        }

        @Test
        void paymentId가_없으면_처리할_수_없다() {
            assertThatThrownBy(() -> listener.onRefundApproved(event(null, 1, "RETURN_APPROVED")))
                    .isInstanceOf(AmqpRejectAndDontRequeueException.class);
            verify(paymentCancelService, never()).cancel(anyLong(), any(), anyString());
        }
    }

    @Nested
    @DisplayName("일시적 실패")
    class TransientFailureTest {

        @Test
        void 그_밖의_예외는_올려보내_브로커가_재배달하게_한다() {
            // PG 장애 같은 일시적 실패는 다시 시도하면 성공할 수 있다.
            willThrow(new IllegalStateException("PG timeout"))
                    .given(paymentCancelService).cancel(anyLong(), any(), anyString());

            assertThatThrownBy(() -> listener.onRefundApproved(event(10, 1, null)))
                    .isInstanceOf(IllegalStateException.class)
                    .isNotInstanceOf(AmqpRejectAndDontRequeueException.class);
        }
    }
}
