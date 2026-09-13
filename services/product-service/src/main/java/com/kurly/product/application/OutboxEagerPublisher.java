package com.kurly.product.application;

import com.kurly.product.application.event.OutboxRecordedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 업무 트랜잭션 커밋 직후, 방금 적재된 아웃박스 row를 바로 발행 시도한다.
 */
@Component
@RequiredArgsConstructor
public class OutboxEagerPublisher {

    private final OutboxPublishService outboxPublishService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutboxRecorded(OutboxRecordedEvent event) {
        outboxPublishService.publishOne(event.outboxId());
    }
}
