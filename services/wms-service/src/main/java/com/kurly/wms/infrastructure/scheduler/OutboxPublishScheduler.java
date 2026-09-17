package com.kurly.wms.infrastructure.scheduler;

import com.kurly.wms.application.OutboxPublishService;
import com.kurly.wms.infrastructure.messaging.WmsOutboxProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 백그라운드 워커 기동점. 업무 로직은 application 계층에 두고 여기서는 주기만 정한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublishScheduler {

    private final OutboxPublishService outboxPublishService;
    private final WmsOutboxProperties wmsOutboxProperties;

    @Scheduled(fixedDelayString = "${wms.outbox.publish-delay-ms:5000}")
    public void publishOutbox() {
        try {
            outboxPublishService.publishPending(wmsOutboxProperties.batchSize());
        } catch (RuntimeException e) {
            log.error("아웃박스 발행 주기 실행 실패", e);
        }
    }
}
