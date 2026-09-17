package com.kurly.wms.infrastructure.config;

import com.kurly.wms.infrastructure.messaging.WmsOutboxProperties;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 입고 완료 등 WMS 발행 이벤트 설정.
 *
 * <p>큐와 바인딩은 선언하지 않는다 — 소비하는 쪽(product-service)이 자기 큐를 선언하고
 * 바인딩한다. 발행자가 소비자의 큐까지 만들면 소비자가 늘어날 때마다 발행자를 고쳐야 한다.
 */
@Configuration
@EnableConfigurationProperties(WmsOutboxProperties.class)
public class OutboxMessagingConfig {

    @Bean
    public TopicExchange wmsTopicExchange(WmsOutboxProperties properties) {
        return ExchangeBuilder.topicExchange(properties.exchange()).durable(true).build();
    }

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "wms.outbox.scheduling.enabled", havingValue = "true", matchIfMissing = true)
    static class OutboxSchedulingConfig {
    }
}
