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
        void 한_번_실패해도_PENDING으로_남아_다시_시도된다() {
            // FAILED로 바로 바꾸면 브로커가 잠깐 흔들린 것뿐인데 이벤트가 영영 나가지 않는다.
            PaymentOutbox event = outbox();

            event.recordFailure("broker down", 5);

            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(event.getAttemptCount()).isEqualTo(1);
            assertThat(event.getLastError()).isEqualTo("broker down");
        }

        @Test
        void 실패할수록_다음_시각을_더_뒤로_민다() {
            // 고정 간격이면 브로커 장애가 길어질 때 같은 부하를 계속 실어 회복을 방해한다.
            PaymentOutbox first = outbox();
            first.recordFailure("boom", 5);
            PaymentOutbox second = outbox();
            second.recordFailure("boom", 5);
            second.recordFailure("boom", 5);

            assertThat(second.getNextAttemptAt()).isAfter(first.getNextAttemptAt());
        }

        @Test
        void 상한에_도달하면_FAILED로_멈춘다() {
            PaymentOutbox event = outbox();

            for (int i = 0; i < 3; i++) {
                event.recordFailure("boom", 3);
            }

            assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
            // 발행되지 않았으므로 발행 시각은 없어야 한다.
            assertThat(event.getPublishedAt()).isNull();
            // 멈춘 뒤에는 워커가 집어갈 예정 시각이 없어야 한다.
            assertThat(event.getNextAttemptAt()).isNull();
        }

        @Test
        void 컬럼_길이를_넘는_오류는_잘라서_담는다() {
            // 넘치면 저장이 실패해 발행 실패가 트랜잭션 실패로 번진다.
            PaymentOutbox event = outbox();

            event.recordFailure("x".repeat(500), 5);

            assertThat(event.getLastError()).hasSize(255);
        }

        @Test
        void 발행에_성공하면_마지막_오류를_지운다() {
            PaymentOutbox event = outbox();
            event.recordFailure("broker down", 5);

            event.markPublished();

            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
            assertThat(event.getLastError()).isNull();
        }
    }
}
