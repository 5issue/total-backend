package com.kurly.wms.infrastructure.scheduler;

import com.kurly.wms.application.OutboundOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 백그라운드 워커 기동점. 업무 로직은 application 계층에 두고 여기서는 주기만 정한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboundOrderFailureScheduler {

    private final OutboundOrderService outboundOrderService;

    @Scheduled(fixedDelayString = "${wms.outbound.failure-check-interval-ms:3600000}")
    public void checkStuckOrders() {
        try {
            outboundOrderService.failStuckOrders();
        } catch (RuntimeException e) {
            log.error("출고 전표 실패 처리 주기 실행 실패", e);
        }
    }
}
