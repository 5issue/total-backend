package com.kurly.payment.infrastructure.scheduler;

import com.kurly.payment.application.OutboxPublishService;
import com.kurly.payment.application.PaymentRetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    @Value("${payment.outbox.batch-size:100}")
    private int outboxBatchSize;

    @Value("${payment.retry.batch-size:50}")
    private int retryBatchSize;

    /** 아웃박스 발행. 결제 취소가 주문 서비스에 늦게 전달되면 고객이 취소 상태를 늦게 본다. */
    @Scheduled(fixedDelayString = "${payment.outbox.publish-delay-ms:5000}")
    public void publishOutbox() {
        try {
            outboxPublishService.publishPending(outboxBatchSize);
        } catch (RuntimeException e) {
            log.error("아웃박스 발행 주기 실행 실패", e);
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
