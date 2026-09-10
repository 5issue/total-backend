package com.kurly.payment.application;

import com.kurly.payment.domain.entity.Payment;
import com.kurly.payment.support.PaymentIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 결과를 모른 채 {@code REQUESTED}로 남은 취소의 회수를 확인한다.
 *
 * <p>{@code beginCancel}은 PG를 부르기 직전에 {@code REQUESTED}를 커밋한다. 여기서 프로세스가 죽으면
 * 실패 기록도 재시도 큐 적재도 일어나지 않아, <b>그 취소는 아무도 이어받지 못한다.</b> 고객 돈이
 * 환불되지 않은 채 남는 경로라, 회수가 되는지는 실제 DB에 대고 확인해야 한다.
 */
@PaymentIntegrationTest
class PaymentCancelRecoveryIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final long AMOUNT = 32_000L;
    private static final int BATCH = 100;

    @Autowired PaymentRecordService paymentRecordService;
    @Autowired JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM payment_retries");
        jdbcTemplate.update("DELETE FROM payment_cancels");
        jdbcTemplate.update("DELETE FROM payments");
    }

    /**
     * PG 호출 직전에 죽은 상황을 만든다. 시각은 MySQL의 {@code NOW()}로 계산한다 —
     * JVM의 {@code LocalDateTime}을 JDBC로 바인딩하면 변환되지 않아 접속 타임존과 어긋난다.
     */
    private Long givenStaleRequestedCancel(Long orderId, long minutesAgo) {
        Payment payment = paymentRecordService.createRequested(orderId, USER_ID, AMOUNT);
        paymentRecordService.recordApproval(payment.getId(),
                new com.kurly.payment.application.port.PgClient.Approval("KEY-" + orderId, "카드", null));
        var cancel = paymentRecordService.beginCancel(payment.getId(), "USER_CANCEL", AMOUNT);
        jdbcTemplate.update(
                "UPDATE payment_cancels SET created_at = NOW(6) - INTERVAL ? MINUTE WHERE id = ?",
                minutesAgo, cancel.getId());
        return cancel.getId();
    }

    private List<Map<String, Object>> retries() {
        return jdbcTemplate.queryForList("SELECT payment_id, task_type, status FROM payment_retries");
    }

    private String cancelStatus(Long cancelId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM payment_cancels WHERE id = ?", String.class, cancelId);
    }

    @Nested
    @DisplayName("회수")
    class RecoverTest {

        @Test
        void 유예가_지난_REQUESTED_취소를_재시도_큐로_넘긴다() {
            Long cancelId = givenStaleRequestedCancel(960L, 60);

            assertThat(paymentRecordService.recoverStaleRequestedCancels(BATCH, java.time.LocalDateTime.now()))
                    .containsExactly(cancelId);
            assertThat(retries()).singleElement()
                    .extracting(row -> row.get("task_type"), row -> row.get("status"))
                    .containsExactly("PG_CANCEL", "PENDING");
        }

        @Test
        void 회수된_취소는_실패로_확정된다() {
            // FAILED로 옮겨야 다음 주기가 같은 건을 또 집지 않는다.
            Long cancelId = givenStaleRequestedCancel(961L, 60);

            paymentRecordService.recoverStaleRequestedCancels(BATCH, java.time.LocalDateTime.now());

            assertThat(cancelStatus(cancelId)).isEqualTo("FAILED");
        }

        @Test
        void 두_번_돌려도_재시도_행이_중복되지_않는다() {
            givenStaleRequestedCancel(962L, 60);

            paymentRecordService.recoverStaleRequestedCancels(BATCH, java.time.LocalDateTime.now());
            paymentRecordService.recoverStaleRequestedCancels(BATCH, java.time.LocalDateTime.now());

            assertThat(retries()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("건드리지 않는 것")
    class UntouchedTest {

        @Test
        void 유예_안의_취소는_두고_본다() {
            // 정상 진행 중인 취소를 실패로 확정하면 멀쩡한 흐름을 깬다.
            Long cancelId = givenStaleRequestedCancel(963L, 0);

            assertThat(paymentRecordService.recoverStaleRequestedCancels(
                    BATCH, java.time.LocalDateTime.now().minusMinutes(15))).isEmpty();
            assertThat(cancelStatus(cancelId)).isEqualTo("REQUESTED");
        }

        @Test
        void 이미_결론이_난_취소는_집지_않는다() {
            Long cancelId = givenStaleRequestedCancel(964L, 60);
            paymentRecordService.completeCancel(cancelId, "PG-CANCEL-1");

            assertThat(paymentRecordService.recoverStaleRequestedCancels(
                    BATCH, java.time.LocalDateTime.now())).isEmpty();
        }

        @Test
        void 묶음_크기를_넘겨_집지_않는다() {
            for (long orderId = 970L; orderId < 973L; orderId++) {
                givenStaleRequestedCancel(orderId, 60);
            }

            assertThat(paymentRecordService.recoverStaleRequestedCancels(2, java.time.LocalDateTime.now()))
                    .hasSize(2);
        }
    }
}
