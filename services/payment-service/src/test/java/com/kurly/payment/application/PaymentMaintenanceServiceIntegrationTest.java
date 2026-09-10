package com.kurly.payment.application;

import com.kurly.payment.support.PaymentIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정리 배치의 {@code DELETE ... ORDER BY ... LIMIT} 네이티브 쿼리를 확인한다.
 *
 * <p>지우는 쿼리는 조건이 틀렸을 때 대가가 크다. 너무 많이 지우면 아직 필요한 기록이 사라지고,
 * 너무 적게 지우면 정리가 되지 않는다. 어느 쪽도 목으로는 드러나지 않는다.
 */
@PaymentIntegrationTest
class PaymentMaintenanceServiceIntegrationTest {

    private static final Duration RETENTION = Duration.ofDays(7);
    private static final int BATCH = 500;
    private static final long HOUR = 60;
    private static final long TEN_DAYS = 10 * 24 * 60;

    @Autowired PaymentMaintenanceService paymentMaintenanceService;
    @Autowired JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM idempotency_keys");
        jdbcTemplate.update("DELETE FROM payment_outbox");
    }

    /**
     * <b>시각은 MySQL의 {@code NOW()}로 계산한다.</b> JVM의 {@code LocalDateTime}을 JDBC로
     * 바인딩하면 변환 없이 그대로 저장되는데, 배치가 넘기는 기준 시각은 Hibernate가 접속
     * 타임존(UTC)으로 변환해 바인딩한다. JVM이 KST면 두 값이 9시간 어긋난다.
     */
    private void givenKey(String key, String status, long minutesAgo) {
        jdbcTemplate.update("""
                INSERT INTO idempotency_keys
                    (user_id, idempotency_key, request_path, request_fingerprint, status, created_at, updated_at)
                VALUES (1, ?, '/api/v1/payments', REPEAT('a', 64), ?,
                        NOW(6) - INTERVAL ? MINUTE, NOW(6) - INTERVAL ? MINUTE)
                """, key, status, minutesAgo, minutesAgo);
    }

    /** {@code minutesAgo}가 음수면 미발행({@code published_at IS NULL})으로 심는다. */
    private void givenOutbox(String tag, String status, long minutesAgo) {
        jdbcTemplate.update("""
                INSERT INTO payment_outbox
                    (event_id, event_type, status, payload, created_at, published_at, attempt_count)
                VALUES (UUID(), 'PAYMENT_CANCELED', ?, JSON_OBJECT('tag', ?),
                        NOW(6) - INTERVAL ? MINUTE,
                        IF(? < 0, NULL, NOW(6) - INTERVAL ? MINUTE), 0)
                """, status, tag, Math.abs(minutesAgo), minutesAgo, minutesAgo);
    }

    private List<String> remainingKeys() {
        return jdbcTemplate.queryForList(
                "SELECT idempotency_key FROM idempotency_keys ORDER BY idempotency_key", String.class);
    }

    @Nested
    @DisplayName("매달린 멱등키 선점 해제")
    class ReleaseStaleTest {

        @Test
        void 오래된_IN_PROGRESS만_지운다() {
            // 풀지 않으면 그 키로 오는 재요청이 영원히 409가 된다.
            givenKey("STALE", "IN_PROGRESS", HOUR);
            givenKey("FRESH", "IN_PROGRESS", 0);
            givenKey("DONE", "COMPLETED", HOUR);

            paymentMaintenanceService.releaseStaleIdempotencyKeys(BATCH, Duration.ofMinutes(30));

            assertThat(remainingKeys()).containsExactly("DONE", "FRESH");
        }

        @Test
        void 묶음_크기를_넘겨_지우지_않는다() {
            // 한 트랜잭션이 테이블을 오래 잠그지 않도록 묶어 둔 것이 실제로 지켜져야 한다.
            for (int i = 0; i < 3; i++) {
                givenKey("STALE-" + i, "IN_PROGRESS", HOUR);
            }

            paymentMaintenanceService.releaseStaleIdempotencyKeys(2, Duration.ofMinutes(30));

            assertThat(remainingKeys()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("보관 기간 정리")
    class PurgeTest {

        @Test
        void 보관_기간이_지난_COMPLETED만_지운다() {
            givenKey("OLD-DONE", "COMPLETED", TEN_DAYS);
            givenKey("NEW-DONE", "COMPLETED", 0);
            givenKey("OLD-PENDING", "IN_PROGRESS", TEN_DAYS);

            paymentMaintenanceService.purgeExpiredIdempotencyKeys(BATCH, RETENTION);

            // IN_PROGRESS는 아직 결론이 안 난 결제의 잠금이다. 보관 기준으로 성급히 풀면 안 된다.
            assertThat(remainingKeys()).containsExactly("NEW-DONE", "OLD-PENDING");
        }

        @Test
        void 발행을_마친_지_오래된_아웃박스만_지운다() {
            givenOutbox("old", "PUBLISHED", TEN_DAYS);
            givenOutbox("new", "PUBLISHED", 0);
            givenOutbox("failed", "FAILED", -TEN_DAYS);

            paymentMaintenanceService.purgePublishedOutbox(BATCH, RETENTION);

            // FAILED는 사람이 봐야 하는 상태다. 정리 배치가 조용히 치우면 무엇이 잘못됐는지 알 수 없다.
            assertThat(jdbcTemplate.queryForList(
                    "SELECT JSON_UNQUOTE(JSON_EXTRACT(payload, '$.tag')) FROM payment_outbox", String.class))
                    .containsExactlyInAnyOrder("new", "failed");
        }
    }
}
