package com.kurly.product.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WMS가 발행하는 입고/출고 완료 이벤트 구독 설정. 큐와 바인딩은 소비자인 우리가 선언한다.
 */
@ConfigurationProperties(prefix = "product.wms-event")
public record WmsEventMessagingProperties(
        String exchange,
        String inboundCompletedRoutingKey,
        String inboundCompletedQueue,
        String outboundCompletedRoutingKey,
        String outboundCompletedQueue,
        String deadLetter
) {

    public WmsEventMessagingProperties {
        exchange = orDefault(exchange, "wms.topic.exchange");
        inboundCompletedRoutingKey = orDefault(inboundCompletedRoutingKey, "wms.inbound.completed");
        inboundCompletedQueue = orDefault(inboundCompletedQueue, "product.inbound.completed.queue");
        outboundCompletedRoutingKey = orDefault(outboundCompletedRoutingKey, "wms.outbound.completed");
        outboundCompletedQueue = orDefault(outboundCompletedQueue, "product.outbound.completed.queue");
        deadLetter = orDefault(deadLetter, "product.wms-event.dlq");
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
