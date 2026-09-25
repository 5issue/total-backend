package com.kurly.wms.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** order-service가 발행하는 주문 이벤트 구독 설정. 큐와 바인딩은 소비자인 우리가 선언한다. */
@ConfigurationProperties(prefix = "wms.order")
public record OrderMessagingProperties(
        String exchange,
        String confirmRoutingKey,
        String confirmQueue,
        String deadLetter
) {

    public OrderMessagingProperties {
        exchange = orDefault(exchange, "order.topic.exchange");
        confirmRoutingKey = orDefault(confirmRoutingKey, "order.inventory.confirm");
        confirmQueue = orDefault(confirmQueue, "wms.inventory.confirm.queue");
        deadLetter = orDefault(deadLetter, "wms.inventory.confirm.dlq");
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
