package com.kurly.product.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 상품 서비스가 발행하는 아웃박스 이벤트(재고 확정/복구 결과)의 발행 설정.
 */
@ConfigurationProperties(prefix = "product.outbox")
public record ProductOutboxProperties(
        String exchange,
        int batchSize
) {

    public ProductOutboxProperties {
        exchange = orDefault(exchange, "product.topic.exchange");
        batchSize = batchSize <= 0 ? 100 : batchSize;
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
