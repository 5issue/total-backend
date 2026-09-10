package com.kurly.payment.infrastructure.scheduler;

import com.kurly.payment.application.OutboxPublishService;
import com.kurly.payment.application.PaymentMaintenanceService;
import com.kurly.payment.application.PaymentReconciliationService;
import com.kurly.payment.application.PaymentRetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 백그라운드 워커 기동점.
 *
 * <p>업무 로직은 application 계층에 두고 여기서는 주기만 정한다. 스케줄 애노테이션이 서비스에 붙어
 * 있으면 테스트에서 원치 않는 실행이 끼어들고, 주기를 바꾸려고 업무 코드를 건드리게 된다.
 *
 * <p><b>예외를 밖으로 내보내지 않는다.</b> {@code @Scheduled} 메서드에서 예외가 나가면 그 작업은
 * 다음 주기에 다시 실행되긴 하지만, 로그가 프레임워크 형식으로만 남아 원인을 찾기 어렵다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentWorkerScheduler {

    private final OutboxPublishService outboxPublishService;
    private final PaymentRetryService paymentRetryService;
    private final PaymentReconciliationService paymentReconciliationService;
    private final PaymentMaintenanceService paymentMaintenanceService;

    @Value("${payment.outbox.batch-size:100}")
    private int outboxBatchSize;

    /** 이 횟수만큼 발행에 실패한 이벤트는 FAILED로 멈춘다. 재시도 큐와 같은 값을 쓴다. */
    @Value("${payment.outbox.max-attempts:5}")
    private int outboxMaxAttempts;

    @Value("${payment.retry.batch-size:50}")
    private int retryBatchSize;

    @Value("${payment.reconcile.batch-size:100}")
    private int reconcileBatchSize;

    /**
     * 대사를 시작하기까지의 유예. 승인이 정상적으로 진행 중인 결제를 대사가 함께 집으면
     * 같은 결제를 두고 두 흐름이 경합한다. PG 승인 타임아웃보다 넉넉히 길어야 한다.
     */
    @Value("${payment.reconcile.stale-after:PT10M}")
    private Duration reconcileStaleAfter;

    @Value("${payment.cleanup.batch-size:500}")
    private int cleanupBatchSize;

    /**
     * 완료 기록 보관 기간. 짧게 잡으면 멱등키가 사라진 뒤 온 재요청이 결제를 새로 실행한다.
     * 클라이언트 재시도는 길어야 분 단위이므로 일 단위면 겹치지 않는다.
     */
    @Value("${payment.cleanup.retention:P7D}")
    private Duration cleanupRetention;

    @Value("${payment.idempotency.batch-size:200}")
    private int idempotencyBatchSize;

    /** 멱등키 선점 해제 유예. 결제 대사가 결론을 낸 뒤에 풀리도록 대사 유예보다 길게 잡는다. */
    @Value("${payment.idempotency.stale-after:PT30M}")
    private Duration idempotencyStaleAfter;

    /** 아웃박스 발행. 결제 취소가 주문 서비스에 늦게 전달되면 고객이 취소 상태를 늦게 본다. */
    @Scheduled(fixedDelayString = "${payment.outbox.publish-delay-ms:5000}")
    public void publishOutbox() {
        try {
            outboxPublishService.publishPending(outboxBatchSize, outboxMaxAttempts);
        } catch (RuntimeException e) {
            log.error("아웃박스 발행 주기 실행 실패", e);
        }
    }

    /**
     * PG 대사. 승인 타임아웃으로 우리 기록과 PG의 진실이 어긋난 결제를 찾아 맞춘다.
     * 방치하면 고객 돈만 빠져나가고 주문은 진행되지 않은 상태가 영영 드러나지 않는다.
     */
    @Scheduled(fixedDelayString = "${payment.reconcile.delay-ms:300000}")
    public void reconcilePayments() {
        try {
            paymentReconciliationService.reconcileStalePayments(reconcileBatchSize, reconcileStaleAfter);
        } catch (RuntimeException e) {
            log.error("PG 대사 주기 실행 실패", e);
        }
    }

    /** 매달린 멱등키 선점 해제. 풀지 않으면 그 키로 오는 재요청이 영원히 409가 된다. */
    @Scheduled(fixedDelayString = "${payment.idempotency.delay-ms:300000}")
    public void releaseStaleIdempotencyKeys() {
        try {
            paymentMaintenanceService.releaseStaleIdempotencyKeys(
                    idempotencyBatchSize, idempotencyStaleAfter);
        } catch (RuntimeException e) {
            log.error("멱등키 선점 해제 주기 실행 실패", e);
        }
    }

    /**
     * 보관 기간이 지난 완료 기록 정리. 멱등키와 아웃박스는 결제마다 늘고 스스로 줄지 않는다.
     * 자주 돌 이유가 없어 하루 한 번으로 둔다.
     */
    @Scheduled(fixedDelayString = "${payment.cleanup.delay-ms:86400000}",
            initialDelayString = "${payment.cleanup.initial-delay-ms:60000}")
    public void purgeExpiredRecords() {
        try {
            paymentMaintenanceService.purgeExpiredIdempotencyKeys(cleanupBatchSize, cleanupRetention);
            paymentMaintenanceService.purgePublishedOutbox(cleanupBatchSize, cleanupRetention);
        } catch (RuntimeException e) {
            log.error("보관 기간 정리 주기 실행 실패", e);
        }
    }

    /** PG 취소 재시도. 실패한 취소를 방치하면 고객 돈이 묶인 채로 남는다. */
    @Scheduled(fixedDelayString = "${payment.retry.delay-ms:60000}")
    public void runRetries() {
        try {
            paymentRetryService.runDueTasks(retryBatchSize);
        } catch (RuntimeException e) {
            log.error("재시도 배치 주기 실행 실패", e);
        }
    }
}
