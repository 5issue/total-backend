package com.kurly.payment.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 결제 이벤트 발행 설정.
 *
 * @param exchange 결제 이벤트를 내보낼 토픽 익스체인지. 큐와 바인딩은 <b>소비하는 서비스가</b>
 *                 선언한다. 발행자가 소비자의 큐까지 만들면 소비자가 늘 때마다 발행자를 고쳐야 한다
 */
@ConfigurationProperties(prefix = "payment.messaging")
public record PaymentMessagingProperties(String exchange) {

    private static final String DEFAULT_EXCHANGE = "payment.topic.exchange";

    public PaymentMessagingProperties {
        exchange = (exchange == null || exchange.isBlank()) ? DEFAULT_EXCHANGE : exchange;
    }
}
