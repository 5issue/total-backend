package com.kurly.wms.infrastructure.scheduler;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 출고 전표가 재고를 전혀 확보하지 못한(UNALLOCATED) 채로 얼마나 오래 버틸 수 있는지 설정. */
@ConfigurationProperties(prefix = "wms.outbound")
public record OutboundFailureProperties(Duration allocationTimeout) {

    public OutboundFailureProperties {
        allocationTimeout = (allocationTimeout == null) ? Duration.ofHours(2) : allocationTimeout;
    }
}
