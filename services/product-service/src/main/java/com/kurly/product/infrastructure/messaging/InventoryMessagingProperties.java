package com.kurly.product.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 주문 재고 선점/해제/확정 이벤트 구독 설정. 큐와 바인딩은 소비자인 우리가 선언한다.
 */
@ConfigurationProperties(prefix = "product.inventory")
public record InventoryMessagingProperties(
        String exchange,
        String holdRoutingKey,
        String holdQueue,
        String releaseRoutingKey,
        String releaseQueue,
        String confirmRoutingKey,
        String confirmQueue,
        String restoreRoutingKey,
        String restoreQueue,
        String deadLetter
) {

    public InventoryMessagingProperties {
        exchange = orDefault(exchange, "order.topic.exchange");
        releaseRoutingKey = orDefault(releaseRoutingKey, "order.inventory.release");
        releaseQueue = orDefault(releaseQueue, "product.inventory.release.queue");
        confirmRoutingKey = orDefault(confirmRoutingKey, "order.inventory.confirm");
        confirmQueue = orDefault(confirmQueue, "product.inventory.confirm.queue");
        restoreRoutingKey = orDefault(restoreRoutingKey, "order.inventory.restore");
        restoreQueue = orDefault(restoreQueue, "product.inventory.restore.queue");
        deadLetter = orDefault(deadLetter, "product.inventory.dlq");
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
