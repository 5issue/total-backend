package com.kurly.payment.application;

import com.kurly.payment.application.port.EventPublisher;
import com.kurly.payment.support.PaymentIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;

/**
 * 아웃박스 발행의 <b>SQL 조건과 상태 전이 커밋</b>을 확인한다.
 *
 * <p>발행 대상 조회는 {@code FOR UPDATE SKIP LOCKED} 네이티브 쿼리이고, 백오프로 미뤄둔 건을
 * 걸러내는 조건도 그 안에 있다. 조건이 빠지면 미뤄둔 의미가 사라지는데 목으로는 드러나지 않는다.
 *
 * <p>브로커는 {@link EventPublisher}를 대신 세워 끊는다. 여기서 볼 것은 DB 경로이고, 실제 발행은
 * 어댑터 테스트가 맡는다. 브로커까지 띄우면 테스트가 브로커 상태에 흔들린다.
 */
@PaymentIntegrationTest
class OutboxPublishServiceIntegrationTest {

    private static final int BATCH = 100;
    private static final int MAX_ATTEMPTS = 3;

    @Autowired OutboxPublishService outboxPublishService;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockitoBean EventPublisher eventPublisher;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM payment_outbox");
    }

    /**
     * {@code nextAttemptInMinutes}가 null이면 예정 시각 없이(적재 직후) 심는다.
     *
     * <p><b>시각은 MySQL의 {@code NOW()}로 계산한다.</b> JVM의 {@code LocalDateTime}을 JDBC로
     * 바인딩하면 변환 없이 그대로 저장되는데, 발행 서비스가 넘기는 기준 시각은 Hibernate가
     * 접속 타임존(UTC)으로 변환해 바인딩한다. JVM이 KST면 두 값이 9시간 어긋난다.
     */
    private void givenPending(String tag, int attemptCount, Long nextAttemptInMinutes) {
        jdbcTemplate.update("""
                INSERT INTO payment_outbox
                    (event_id, event_type, status, payload, created_at, attempt_count, next_attempt_at)
                VALUES (UUID(), 'PAYMENT_CANCELED', 'PENDING', JSON_OBJECT('tag', ?), NOW(6), ?,
                        IF(? IS NULL, NULL, NOW(6) + INTERVAL ? MINUTE))
                """, tag, attemptCount, nextAttemptInMinutes, nextAttemptInMinutes);
    }

    private Map<String, Object> row(String tag) {
        return jdbcTemplate.queryForMap("""
                SELECT status, attempt_count, next_attempt_at, last_error, published_at
                  FROM payment_outbox
                 WHERE JSON_UNQUOTE(JSON_EXTRACT(payload, '$.tag')) = ?
                """, tag);
    }

    private void brokerDown() {
        willThrow(new IllegalStateException("broker down"))
                .given(eventPublisher).publish(anyString(), anyString(), anyString());
    }

    @Nested
    @DisplayName("발행 대상 선정")
    class SelectionTest {

        @Test
        void 예정_시각이_없는_건은_바로_대상이다() {
            // 적재 직후가 그렇다. 여기서 걸러지면 이벤트가 첫 발행조차 되지 않는다.
            givenPending("ready", 0, null);

            assertThat(outboxPublishService.publishPending(BATCH, MAX_ATTEMPTS)).isEqualTo(1);
        }

        @Test
        void 백오프로_미뤄둔_건은_아직_집지_않는다() {
            givenPending("deferred", 2, 5L);

            assertThat(outboxPublishService.publishPending(BATCH, MAX_ATTEMPTS)).isZero();
            assertThat(row("deferred").get("status")).isEqualTo("PENDING");
        }

        @Test
        void 예정_시각이_지난_건은_다시_집는다() {
            givenPending("due", 2, -1L);

            assertThat(outboxPublishService.publishPending(BATCH, MAX_ATTEMPTS)).isEqualTo(1);
            assertThat(row("due").get("status")).isEqualTo("PUBLISHED");
        }

        @Test
        void 멈춘_건은_다시_집지_않는다() {
            // FAILED는 사람이 봐야 하는 상태다. 배치가 계속 집으면 멈춘 의미가 없다.
            jdbcTemplate.update("""
                    INSERT INTO payment_outbox
                        (event_id, event_type, status, payload, created_at, attempt_count)
                    VALUES (UUID(), 'PAYMENT_CANCELED', 'FAILED', JSON_OBJECT('tag', 'stopped'), NOW(6), 3)
                    """);

            assertThat(outboxPublishService.publishPending(BATCH, MAX_ATTEMPTS)).isZero();
        }
    }

    @Nested
    @DisplayName("발행 실패")
    class FailureTest {

        @Test
        void 실패는_시도_횟수와_예정_시각으로_커밋된다() {
            givenPending("failing", 0, null);
            brokerDown();

            outboxPublishService.publishPending(BATCH, MAX_ATTEMPTS);

            Map<String, Object> stored = row("failing");
            assertThat(stored.get("status")).isEqualTo("PENDING");
            assertThat(stored.get("attempt_count")).isEqualTo(1);
            assertThat(stored.get("next_attempt_at")).isNotNull();
            assertThat(stored.get("last_error")).asString().contains("broker down");
        }

        @Test
        void 상한에_도달하면_FAILED로_멈춘다() {
            // 나가지 않는 이벤트가 배치 묶음을 계속 차지하면 뒤에 쌓인 정상 이벤트가 밀린다.
            givenPending("poison", MAX_ATTEMPTS - 1, null);
            brokerDown();

            outboxPublishService.publishPending(BATCH, MAX_ATTEMPTS);

            Map<String, Object> stored = row("poison");
            assertThat(stored.get("status")).isEqualTo("FAILED");
            assertThat(stored.get("attempt_count")).isEqualTo(MAX_ATTEMPTS);
            // 멈춘 뒤에는 워커가 집어갈 예정 시각이 없어야 한다.
            assertThat(stored.get("next_attempt_at")).isNull();
            assertThat(stored.get("published_at")).isNull();
        }

        @Test
        void 한_건이_실패해도_나머지는_계속_보낸다() {
            givenPending("ok", 0, null);
            givenPending("poison", MAX_ATTEMPTS - 1, null);
            willThrow(new IllegalStateException("broker down"))
                    .given(eventPublisher).publish(anyString(), anyString(), argThatIsPoison());

            outboxPublishService.publishPending(BATCH, MAX_ATTEMPTS);

            assertThat(row("ok").get("status")).isEqualTo("PUBLISHED");
            assertThat(row("poison").get("status")).isEqualTo("FAILED");
        }

        private static String argThatIsPoison() {
            return org.mockito.ArgumentMatchers.contains("poison");
        }
    }
}
