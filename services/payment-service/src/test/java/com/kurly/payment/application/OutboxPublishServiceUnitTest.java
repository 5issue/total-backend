package com.kurly.payment.application;

import com.kurly.payment.application.port.EventPublisher;
import com.kurly.payment.domain.entity.PaymentOutbox;
import com.kurly.payment.domain.enums.OutboxStatus;
import com.kurly.payment.domain.repository.PaymentOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxPublishServiceUnitTest {

    @Mock PaymentOutboxRepository paymentOutboxRepository;
    @Mock EventPublisher eventPublisher;
    @InjectMocks OutboxPublishService outboxPublishService;

    private static PaymentOutbox event() {
        return PaymentOutbox.builder()
                .eventType("PAYMENT_CANCELED")
                .payload("{\"paymentId\":1,\"userId\":1}")
                .build();
    }

    @Nested
    @DisplayName("발행")
    class PublishTest {

        @Test
        void 미발행_이벤트를_보내고_PUBLISHED로_바꾼다() {
            PaymentOutbox pending = event();
            given(paymentOutboxRepository.findPendingForUpdateSkipLocked(100)).willReturn(List.of(pending));

            assertThat(outboxPublishService.publishPending(100)).isEqualTo(1);

            verify(eventPublisher).publish(pending.getEventId(), "PAYMENT_CANCELED", pending.getPayload());
            assertThat(pending.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
            assertThat(pending.getPublishedAt()).isNotNull();
        }

        @Test
        void 보낼_것이_없으면_아무_일도_하지_않는다() {
            given(paymentOutboxRepository.findPendingForUpdateSkipLocked(anyInt())).willReturn(List.of());

            assertThat(outboxPublishService.publishPending(100)).isZero();

            verify(eventPublisher, never()).publish(anyString(), anyString(), anyString());
        }

        @Test
        void 이벤트_ID를_함께_넘긴다() {
            // 소비자가 중복 처리를 거르는 기준이다. 발행은 최소 1회를 보장한다.
            PaymentOutbox pending = event();
            given(paymentOutboxRepository.findPendingForUpdateSkipLocked(100)).willReturn(List.of(pending));

            outboxPublishService.publishPending(100);

            verify(eventPublisher).publish(eq(pending.getEventId()), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("발행 실패")
    class FailureTest {

        @Test
        void 실패한_건은_PENDING으로_남겨_다음_주기에_다시_시도한다() {
            // FAILED로 바꿔버리면 브로커가 잠깐 흔들린 것뿐인데 이벤트가 영영 나가지 않는다.
            PaymentOutbox pending = event();
            given(paymentOutboxRepository.findPendingForUpdateSkipLocked(100)).willReturn(List.of(pending));
            willThrow(new IllegalStateException("broker down"))
                    .given(eventPublisher).publish(anyString(), anyString(), anyString());

            assertThat(outboxPublishService.publishPending(100)).isZero();

            assertThat(pending.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(pending.getPublishedAt()).isNull();
        }

        @Test
        void 한_건이_실패해도_나머지는_계속_보낸다() {
            PaymentOutbox failing = event();
            PaymentOutbox succeeding = event();
            given(paymentOutboxRepository.findPendingForUpdateSkipLocked(100))
                    .willReturn(List.of(failing, succeeding));
            willThrow(new IllegalStateException("boom"))
                    .given(eventPublisher).publish(eq(failing.getEventId()), anyString(), anyString());

            assertThat(outboxPublishService.publishPending(100)).isEqualTo(1);

            assertThat(failing.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(succeeding.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        }
    }
}
