package com.kurly.payment.infrastructure.config;

import com.kurly.payment.infrastructure.messaging.RefundMessagingProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 반품 환불 이벤트 소비 설정.
 *
 * <p>큐와 바인딩은 소비자가 선언한다. OMS의 익스체인지도 함께 선언하지만, 익스체인지 선언은
 * 멱등이라 발행자가 먼저 만들었어도 문제가 되지 않는다. 이렇게 해두면 <b>기동 순서에 의존하지 않는다</b> —
 * OMS보다 먼저 떠도 큐가 만들어져 그동안의 이벤트를 놓치지 않는다.
 */
@Configuration
@EnableConfigurationProperties(RefundMessagingProperties.class)
public class RefundMessagingConfig {

    @Bean
    public TopicExchange orderTopicExchange(RefundMessagingProperties properties) {
        return ExchangeBuilder.topicExchange(properties.exchange()).durable(true).build();
    }

    /**
     * 처리할 수 없는 메시지가 가는 곳.
     *
     * <p>없으면 그런 메시지가 무한히 재배달되며 큐를 막는다. 환불은 돈이 걸린 흐름이라
     * 막히면 다른 고객의 환불까지 지연된다.
     */
    @Bean
    public Queue refundDeadLetterQueue(RefundMessagingProperties properties) {
        return QueueBuilder.durable(properties.deadLetter()).build();
    }

    @Bean
    public Queue refundQueue(RefundMessagingProperties properties) {
        return QueueBuilder.durable(properties.queue())
                // 기본 익스체인지로 보내면 라우팅 키가 곧 큐 이름이 된다.
                .deadLetterExchange("")
                .deadLetterRoutingKey(properties.deadLetter())
                .build();
    }

    @Bean
    public Binding refundBinding(Queue refundQueue, TopicExchange orderTopicExchange,
                                 RefundMessagingProperties properties) {
        return BindingBuilder.bind(refundQueue).to(orderTopicExchange).with(properties.routingKey());
    }
}
