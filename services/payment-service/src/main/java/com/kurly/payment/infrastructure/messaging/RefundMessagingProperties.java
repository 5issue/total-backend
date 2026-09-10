package com.kurly.payment.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 반품 환불 이벤트 소비 설정(주문-결제 시퀀스 3절).
 *
 * <p>큐와 바인딩은 <b>소비자가 선언한다.</b> 발행자(OMS)가 소비자의 큐까지 만들면 소비자가 늘 때마다
 * 발행자를 고쳐야 한다.
 *
 * @param exchange   OMS가 환불 승인 이벤트를 발행하는 익스체인지
 * @param routingKey 구독할 라우팅 키
 * @param queue      우리 큐
 * @param deadLetter 처리할 수 없는 메시지를 보낼 큐. 무한 재시도로 큐가 막히는 것을 막는다
 */
@ConfigurationProperties(prefix = "payment.refund")
public record RefundMessagingProperties(String exchange, String routingKey,
                                        String queue, String deadLetter) {

    public RefundMessagingProperties {
        exchange = orDefault(exchange, "order.topic.exchange");
        routingKey = orDefault(routingKey, "order.refund.requested");
        queue = orDefault(queue, "payment.refund.queue");
        deadLetter = orDefault(deadLetter, "payment.refund.dlq");
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
