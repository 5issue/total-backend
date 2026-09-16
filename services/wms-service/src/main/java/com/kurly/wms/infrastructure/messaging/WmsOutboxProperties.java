package com.kurly.wms.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** WMS가 발행하는 아웃박스 이벤트(입고 완료 등)의 발행 설정. */
@ConfigurationProperties(prefix = "wms.outbox")
public record WmsOutboxProperties(
        String exchange,
        int batchSize
) {

    public WmsOutboxProperties {
        exchange = orDefault(exchange, "wms.topic.exchange");
        batchSize = batchSize <= 0 ? 100 : batchSize;
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
