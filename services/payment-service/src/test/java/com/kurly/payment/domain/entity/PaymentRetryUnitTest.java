package com.kurly.payment.domain.entity;

import com.kurly.payment.domain.enums.RetryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentRetryUnitTest {

    private static final int MAX_RETRY = 5;

    private static PaymentRetry retry() {
        return PaymentRetry.builder()
                .payment(Payment.builder().orderId(111L).userId(1L).totalAmount(32_000L).build())
                .taskType("PG_CANCEL")
                .payload("{\"paymentCancelId\":20}")
                .build();
    }

    /** 예정 시각까지 남은 분. 실행 시각 차이를 흡수하려고 분 단위로 본다. */
    private static long minutesUntilNextRetry(PaymentRetry retry) {
        return Duration.between(LocalDateTime.now(), retry.getNextRetryAt()).toMinutes();
    }

    @Nested
    @DisplayName("지수 백오프")
    class BackoffTest {

        @Test
        void 시도할수록_간격이_두_배로_늘어난다() {
            // 고정 간격이면 PG 장애가 길어질 때 같은 부하를 계속 실어 회복을 방해한다.
            PaymentRetry retry = retry();

            retry.recordFailure("boom", MAX_RETRY);
            assertThat(minutesUntilNextRetry(retry)).isZero();      // 1분 뒤 (경과분 반올림)

            retry.recordFailure("boom", MAX_RETRY);
            assertThat(minutesUntilNextRetry(retry)).isEqualTo(1);  // 2분 뒤

            retry.recordFailure("boom", MAX_RETRY);
            assertThat(minutesUntilNextRetry(retry)).isEqualTo(3);  // 4분 뒤

            retry.recordFailure("boom", MAX_RETRY);
            assertThat(minutesUntilNextRetry(retry)).isEqualTo(7);  // 8분 뒤
        }

        @Test
        void 실패할_때마다_횟수와_사유를_남긴다() {
            PaymentRetry retry = retry();

            retry.recordFailure("PG timeout", MAX_RETRY);

            assertThat(retry.getRetryCount()).isEqualTo(1);
            assertThat(retry.getLastError()).isEqualTo("PG timeout");
            assertThat(retry.getStatus()).isEqualTo(RetryStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("중단")
    class ExhaustionTest {

        @Test
        void 상한에_도달하면_FAILED로_멈춘다() {
            PaymentRetry retry = retry();

            for (int i = 0; i < MAX_RETRY; i++) {
                retry.recordFailure("boom", MAX_RETRY);
            }

            assertThat(retry.getStatus()).isEqualTo(RetryStatus.FAILED);
            // 배치가 다시 집어가지 않도록 예정 시각을 비운다.
            assertThat(retry.getNextRetryAt()).isNull();
        }
    }

    @Nested
    @DisplayName("성공")
    class SuccessTest {

        @Test
        void 성공하면_예정_시각과_오류를_지운다() {
            PaymentRetry retry = retry();
            retry.recordFailure("boom", MAX_RETRY);

            retry.succeed();

            assertThat(retry.getStatus()).isEqualTo(RetryStatus.SUCCESS);
            assertThat(retry.getNextRetryAt()).isNull();
            assertThat(retry.getLastError()).isNull();
        }
    }
}
