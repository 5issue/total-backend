package com.kurly.product.infrastructure.config;

import com.kurly.product.infrastructure.messaging.ProductOutboxProperties;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 재고 확정/복구 결과 발행 설정.
 *
 * <p>큐와 바인딩은 선언하지 않는다 — 소비하는 쪽(주문 서비스)이 자기 큐를 선언하고 바인딩한다.
 * 발행자가 소비자의 큐까지 만들면 소비자가 늘어날 때마다 발행자를 고쳐야 한다.
 */
@Configuration
@EnableConfigurationProperties(ProductOutboxProperties.class)
public class OutboxMessagingConfig {

    @Bean
    public TopicExchange productTopicExchange(ProductOutboxProperties properties) {
        return ExchangeBuilder.topicExchange(properties.exchange()).durable(true).build();
    }

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "product.outbox.scheduling.enabled", havingValue = "true", matchIfMissing = true)
    static class OutboxSchedulingConfig {
    }
}
