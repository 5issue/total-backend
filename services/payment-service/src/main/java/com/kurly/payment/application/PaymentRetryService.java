package com.kurly.payment.application;

import com.kurly.payment.application.port.PgClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 재시도 배치.
 *
 * <p>PG 취소가 실패하면 고객 돈이 묶인 채로 남는다. 그 상태를 사람 손 없이 되돌리는 것이 이 배치의
 * 목적이다(주문-결제 시퀀스 1절 단계 3).
 *
 * <p><b>트랜잭션을 걸지 않는다.</b> PG 호출이 섞여 있어 감싸면 응답을 기다리는 내내 커넥션을 붙잡는다.
 * DB 작업은 {@link PaymentRecordService}의 짧은 트랜잭션이 맡는다.
 *
 * <p><b>상한에 도달하면 영구 실패로 전환하고 배치는 손을 뗀다.</b> 자동으로 되돌릴 방법을 다 쓴
 * 상태이므로 사람이 개입해야 한다. 영구 실패 건({@code payment_retries.status = 'FAILED'})은
 * 관리자 대시보드에 노출해 PG 관리자 콘솔에서 직접 취소하거나 고객에게 안내하도록 한다.
 * <b>대시보드와 알림은 아직 구현되지 않았다</b> — 현재는 테이블을 직접 조회해야 확인할 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRetryService {

    /**
     * 선점 시간. PG 호출이 이 시간 안에 끝나지 않으면 다른 인스턴스가 같은 건을 다시 집어갈 수 있다.
     * PG 타임아웃보다 넉넉하게 잡는다.
     */
    static final Duration LEASE = Duration.ofMinutes(5);

    private final PaymentRecordService paymentRecordService;
    private final PgClient pgClient;

    /**
     * 실행 시각이 된 작업을 한 묶음 처리한다.
     *
     * @return 성공한 건수
     */
    public int runDueTasks(int batchSize) {
        List<PaymentRecordService.RetryTask> tasks =
                paymentRecordService.claimDueRetries(batchSize, LEASE);
        if (tasks.isEmpty()) {
            return 0;
        }

        int succeeded = 0;
        for (PaymentRecordService.RetryTask task : tasks) {
            if (runTask(task)) {
                succeeded++;
            }
        }
        log.info("재시도 배치 완료: 대상={}, 성공={}", tasks.size(), succeeded);
        return succeeded;
    }

    private boolean runTask(PaymentRecordService.RetryTask task) {
        if (!PaymentRecordService.PG_CANCEL_TASK.equals(task.taskType())) {
            // 처리할 줄 모르는 작업을 계속 집어가면 배치가 헛돈다. 실패로 기록해 상한에 걸리게 한다.
            log.error("알 수 없는 재시도 작업 종류: retryId={}, taskType={}",
                    task.retryId(), task.taskType());
            paymentRecordService.failRetry(task.retryId(), "지원하지 않는 작업: " + task.taskType());
            return false;
        }
        try {
            PgClient.Cancellation cancellation =
                    pgClient.cancel(task.paymentKey(), task.cancelAmount(), "RETRY", task.paymentCancelId());
            paymentRecordService.completeRetry(
                    task.retryId(), task.paymentCancelId(), cancellation.pgCancelKey());
            log.info("재시도 취소 성공: retryId={}, paymentCancelId={}",
                    task.retryId(), task.paymentCancelId());
            return true;
        } catch (RuntimeException e) {
            // 상한에 도달하면 FAILED로 멈춘다. 그때부터는 사람이 봐야 한다.
            log.error("재시도 취소 실패: retryId={}", task.retryId(), e);
            paymentRecordService.failRetry(task.retryId(), e.getMessage());
            return false;
        }
    }
}
