package com.kurly.payment.application;

import com.kurly.payment.domain.entity.Payment;
import com.kurly.payment.domain.enums.PaymentStatus;
import com.kurly.payment.infrastructure.persistence.PaymentJpaRepository;
import com.kurly.payment.support.PaymentIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 대사 대상 선정과 선점의 <b>SQL·트랜잭션 경계</b>를 확인한다.
 *
 * <p>{@code claimReconcilable}은 네이티브 쿼리로 조건을 걸고 선점 표시를 같은 트랜잭션에 커밋한다.
 * 조건이 하나라도 어긋나면 진행 중인 정상 결제를 집어가거나, 맞춰야 할 건을 영영 건너뛴다.
 * 목으로는 어느 쪽도 드러나지 않는다.
 */
@PaymentIntegrationTest
class PaymentRecordServiceIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final long AMOUNT = 32_000L;
    /**
     * 유예 기준. 이 시각보다 앞선 결제만 대사 대상이다.
     * 인자로 넘기는 값은 Hibernate가 바인딩하므로 저장된 값과 같은 시계에 놓인다.
     */
    private static final LocalDateTime STALE_BEFORE = LocalDateTime.now().minusMinutes(10);

    /** 테스트에서는 임대가 만료되지 않을 만큼만 길면 된다. */
    private static final java.time.Duration LEASE = java.time.Duration.ofMinutes(5);

    private static final long HOUR_AGO = 60;
    private static final long MINUTE_AGO = 1;

    @Autowired PaymentRecordService paymentRecordService;
    @Autowired PaymentJpaRepository paymentJpaRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM payment_retries");
        jdbcTemplate.update("DELETE FROM payment_cancels");
        jdbcTemplate.update("DELETE FROM payments");
    }

    /**
     * 요청 시각을 과거로 심는다. requested_at은 엔티티로 바꿀 수 없어 SQL로 직접 민다.
     *
     * <p><b>시각은 MySQL의 {@code NOW()}로 계산한다.</b> JVM의 {@code LocalDateTime}을 JDBC로
     * 바인딩하면 변환 없이 그대로 저장되는데, Hibernate가 쓰는 값은 접속 타임존(UTC)으로 변환된다.
     * JVM이 KST면 두 값이 9시간 어긋나, 프로덕션과 다른 시계로 심은 데이터를 검증하게 된다.
     */
    private Payment given(Long orderId, PaymentStatus status, long minutesAgo) {
        Payment payment = paymentRecordService.createRequested(orderId, USER_ID, AMOUNT);
        jdbcTemplate.update(
                "UPDATE payments SET status = ?, requested_at = NOW(6) - INTERVAL ? MINUTE WHERE id = ?",
                status.name(), minutesAgo, payment.getId());
        return payment;
    }

    private List<Long> claimedIds() {
        return paymentRecordService.claimReconcilable(100, STALE_BEFORE, LEASE).stream()
                .map(PaymentRecordService.ReconcileTarget::paymentId)
                .toList();
    }

    @Nested
    @DisplayName("대사 대상 선정")
    class ClaimTest {

        @Test
        void 유예가_지난_REQUESTED와_FAILED만_집는다() {
            Long requested = given(901L, PaymentStatus.REQUESTED, HOUR_AGO).getId();
            Long failed = given(902L, PaymentStatus.FAILED, HOUR_AGO).getId();

            assertThat(claimedIds()).containsExactlyInAnyOrder(requested, failed);
        }

        @Test
        void 이미_결론이_난_결제는_집지_않는다() {
            // 취소된 결제와, 주문 인계까지 끝난 성공 결제는 더 맞출 것이 없다.
            // (인계되지 않은 SUCCESS는 대사 대상이다 — NotHandedOverTest 참조)
            Long settled = given(903L, PaymentStatus.SUCCESS, HOUR_AGO).getId();
            jdbcTemplate.update("UPDATE payments SET order_notified_at = NOW(6) WHERE id = ?", settled);
            given(904L, PaymentStatus.CANCELED, HOUR_AGO);

            assertThat(claimedIds()).isEmpty();
        }

        @Test
        void 유예_안의_결제는_집지_않는다() {
            // 승인이 정상 진행 중인 결제를 집으면 승인과 대사가 같은 건을 두고 경합한다.
            given(905L, PaymentStatus.REQUESTED, MINUTE_AGO);

            assertThat(claimedIds()).isEmpty();
        }

        @Test
        void 묶음_크기를_넘겨_집지_않는다() {
            for (long orderId = 910L; orderId < 913L; orderId++) {
                given(orderId, PaymentStatus.REQUESTED, HOUR_AGO);
            }

            assertThat(paymentRecordService.claimReconcilable(2, STALE_BEFORE, LEASE)).hasSize(2);
        }
    }

    @Nested
    @DisplayName("선점")
    class ClaimMarkerTest {

        @Test
        void 선점_임대가_조회와_같은_트랜잭션에_커밋된다() {
            // 나중에 표시하면 다른 인스턴스가 같은 결제를 함께 집어 PG에 두 번 묻고 두 번 정정한다.
            Long id = given(920L, PaymentStatus.REQUESTED, HOUR_AGO).getId();

            paymentRecordService.claimReconcilable(100, STALE_BEFORE, LEASE);

            var row = jdbcTemplate.queryForMap(
                    "SELECT reconcile_claimed_until, reconciled_at FROM payments WHERE id = ?", id);
            assertThat(row.get("reconcile_claimed_until")).isNotNull();
            // 선점은 완료가 아니다. 겸하게 두면 워커가 죽었을 때 그 건이 영구히 묻힌다.
            assertThat(row.get("reconciled_at")).isNull();
        }

        @Test
        void 한번_집은_건은_다시_집히지_않는다() {
            given(921L, PaymentStatus.REQUESTED, HOUR_AGO);

            assertThat(claimedIds()).hasSize(1);
            assertThat(claimedIds()).isEmpty();
        }

        @Test
        void 선점을_되돌리면_다음_주기에_다시_집힌다() {
            // 결론을 못 낸 건이 대사 완료로 남으면 영영 다시 보지 않게 된다.
            Long id = given(922L, PaymentStatus.REQUESTED, HOUR_AGO).getId();
            assertThat(claimedIds()).containsExactly(id);

            paymentRecordService.releaseReconciliationClaim(id);

            assertThat(claimedIds()).containsExactly(id);
        }
    }

    @Nested
    @DisplayName("선점 임대 만료")
    class LeaseExpiryTest {

        @Test
        void 임대가_살아있는_동안은_다시_집히지_않는다() {
            given(940L, PaymentStatus.REQUESTED, HOUR_AGO);

            assertThat(claimedIds()).hasSize(1);
            assertThat(claimedIds()).isEmpty();
        }

        @Test
        void 임대가_만료되면_다시_집힌다() {
            // 워커가 선점 직후 죽어도 그 결제가 영구히 묻히면 안 된다.
            // 완료 표시와 선점을 겸하던 예전 구조에서는 여기서 영영 사라졌다.
            Long id = given(941L, PaymentStatus.REQUESTED, HOUR_AGO).getId();
            assertThat(claimedIds()).containsExactly(id);

            jdbcTemplate.update(
                    "UPDATE payments SET reconcile_claimed_until = NOW(6) - INTERVAL 1 MINUTE WHERE id = ?", id);

            assertThat(claimedIds()).containsExactly(id);
        }

        @Test
        void 완료로_표시하면_임대와_무관하게_제외된다() {
            Long id = given(942L, PaymentStatus.REQUESTED, HOUR_AGO).getId();
            paymentRecordService.claimReconcilable(100, STALE_BEFORE, LEASE);

            paymentRecordService.completeReconciliation(id);
            jdbcTemplate.update(
                    "UPDATE payments SET reconcile_claimed_until = NOW(6) - INTERVAL 1 MINUTE WHERE id = ?", id);

            assertThat(claimedIds()).isEmpty();
        }
    }

    @Nested
    @DisplayName("주문에 인계되지 못한 성공 결제")
    class NotHandedOverTest {

        private Long approvedButNotNotified(Long orderId) {
            Payment payment = paymentRecordService.createRequested(orderId, USER_ID, AMOUNT);
            paymentRecordService.recordApproval(payment.getId(),
                    new com.kurly.payment.application.port.PgClient.Approval("KEY-" + orderId, "카드", null));
            jdbcTemplate.update(
                    "UPDATE payments SET requested_at = NOW(6) - INTERVAL ? MINUTE WHERE id = ?",
                    HOUR_AGO, payment.getId());
            return payment.getId();
        }

        @Test
        void 인계되지_않은_성공_결제도_대사가_집는다() {
            // 승인 기록과 주문 인계는 다른 트랜잭션이다. 그 사이에 죽으면
            // "결제는 성공했는데 주문은 모르는" 상태가 남는다.
            Long id = approvedButNotNotified(950L);

            assertThat(claimedIds()).containsExactly(id);
        }

        @Test
        void 인계_표시가_있으면_집지_않는다() {
            Long id = approvedButNotNotified(951L);
            paymentRecordService.markOrderNotified(id);

            assertThat(claimedIds()).isEmpty();
        }

        @Test
        void 인계_대상임을_함께_알려준다() {
            approvedButNotNotified(952L);

            assertThat(paymentRecordService.claimReconcilable(100, STALE_BEFORE, LEASE))
                    .singleElement()
                    .extracting(PaymentRecordService.ReconcileTarget::approvedButNotHandedOver,
                            PaymentRecordService.ReconcileTarget::paymentKey)
                    .containsExactly(true, "KEY-952");
        }
    }

    @Nested
    @DisplayName("주문당 성공 결제 유일성")
    class UniqueSuccessTest {

        private void approve(Payment payment, String paymentKey) {
            paymentRecordService.recordApproval(payment.getId(),
                    new com.kurly.payment.application.port.PgClient.Approval(paymentKey, "카드", null));
        }

        @Test
        void 같은_주문에_두번째_성공은_DB가_막는다() {
            // 애플리케이션 검증을 동시 요청이 함께 통과해도 여기서 걸린다. 마지막 방어선이다.
            Payment first = paymentRecordService.createRequested(930L, USER_ID, AMOUNT);
            Payment second = paymentRecordService.createRequested(930L, USER_ID, AMOUNT);
            approve(first, "KEY-1");

            assertThatThrownBy(() -> approve(second, "KEY-2"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void 취소된_결제가_있는_주문은_다시_결제할_수_있다() {
            // 생성 컬럼이 SUCCESS일 때만 값을 갖는다. 취소되면 제약이 풀려야 재결제가 막히지 않는다.
            Payment canceled = paymentRecordService.createRequested(931L, USER_ID, AMOUNT);
            approve(canceled, "KEY-1");
            jdbcTemplate.update("UPDATE payments SET status = 'CANCELED' WHERE id = ?", canceled.getId());

            Payment retried = paymentRecordService.createRequested(931L, USER_ID, AMOUNT);

            approve(retried, "KEY-2");
            assertThat(paymentJpaRepository.findById(retried.getId()).orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.SUCCESS);
        }
    }
}
