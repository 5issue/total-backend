package com.kurly.payment.domain.entity;

import com.kurly.payment.domain.enums.OutboxStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentOutboxUnitTest {

    private static PaymentOutbox outbox() {
        return PaymentOutbox.builder()
                .eventType("PAYMENT_CANCELED")
                .payload("{\"userId\":1}")
                .build();
    }

    @Nested
    @DisplayName("생성")
    class CreateTest {

        @Test
        void PENDING으로_시작하고_이벤트_ID를_발급한다() {
            PaymentOutbox event = outbox();

            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(event.getPublishedAt()).isNull();
            // 소비자가 중복 처리를 방어하는 기준이다. 발행은 최소 1회를 보장한다.
            assertThat(event.getEventId()).hasSize(36);
        }

        @Test
        void 이벤트_ID는_건마다_다르다() {
            assertThat(outbox().getEventId()).isNotEqualTo(outbox().getEventId());
        }
    }

    @Nested
    @DisplayName("발행 결과")
    class PublishTest {

        @Test
        void 발행되면_시각을_남긴다() {
            PaymentOutbox event = outbox();

            event.markPublished();

            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
            assertThat(event.getPublishedAt()).isNotNull();
        }

        @Test
        void 반복_실패하면_FAILED로_멈춘다() {
            PaymentOutbox event = outbox();

            event.markFailed();

            assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
            // 발행되지 않았으므로 발행 시각은 없어야 한다.
            assertThat(event.getPublishedAt()).isNull();
        }
    }
}
