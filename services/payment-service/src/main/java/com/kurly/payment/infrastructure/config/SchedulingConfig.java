package com.kurly.payment.infrastructure.config;

import com.kurly.payment.infrastructure.messaging.PaymentMessagingProperties;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 이벤트 발행 설정.
 *
 * <p>큐와 바인딩은 선언하지 않는다. <b>소비하는 서비스가 자기 큐를 선언하고 바인딩한다.</b>
 * 발행자가 소비자의 큐까지 만들면 소비자가 늘어날 때마다 발행자를 고쳐야 한다.
 */
@Configuration
@EnableConfigurationProperties(PaymentMessagingProperties.class)
public class SchedulingConfig {

    @Bean
    public TopicExchange paymentTopicExchange(PaymentMessagingProperties properties) {
        // 브로커가 재시작해도 익스체인지가 남아야 발행이 이어진다.
        return ExchangeBuilder.topicExchange(properties.exchange()).durable(true).build();
    }

    /**
     * 백그라운드 워커 활성화.
     *
     * <p>끌 수 있게 해 둔다. 통합 테스트에서 워커가 같이 돌면 테스트가 만든 결제·아웃박스를
     * 집어가 검증 대상이 시험 도중 바뀐다. 운영에서도 워커를 특정 파드에만 두고 싶을 때 쓴다.
     */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "payment.scheduling.enabled", havingValue = "true", matchIfMissing = true)
    static class WorkerSchedulingConfig {
    }
}
