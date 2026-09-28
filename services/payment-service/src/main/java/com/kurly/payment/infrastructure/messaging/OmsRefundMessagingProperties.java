package com.kurly.payment.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OMS 반품 환불 요청 구독 설정(payment 추가 통신 명세).
 *
 * <p><b>큐와 바인딩은 소비자가 선언한다.</b> 발행자(OMS)가 소비자의 큐까지 만들면 소비자가
 * 늘 때마다 발행자를 고쳐야 한다.
 *
 * @param exchange   OMS가 발행하는 익스체인지
 * @param routingKey 구독할 라우팅 키
 * @param queue      우리 큐
 * @param deadLetter 처리할 수 없는 메시지를 보낼 큐. 무한 재배달로 큐가 막히는 것을 막는다
 */
@ConfigurationProperties(prefix = "payment.oms-refund")
public record OmsRefundMessagingProperties(String exchange, String routingKey,
                                           String queue, String deadLetter) {

    public OmsRefundMessagingProperties {
        exchange = orDefault(exchange, "oms.topic.exchange");
        routingKey = orDefault(routingKey, "oms.order-refund.requested");
        queue = orDefault(queue, "payment.oms-refund.queue");
        deadLetter = orDefault(deadLetter, "payment.oms-refund.dlq");
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
