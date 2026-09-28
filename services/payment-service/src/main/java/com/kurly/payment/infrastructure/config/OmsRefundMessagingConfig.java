package com.kurly.payment.infrastructure.config;

import com.kurly.payment.infrastructure.messaging.OmsRefundMessagingProperties;
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
 * OMS 반품 환불 요청 소비 설정(payment 추가 통신 명세).
 *
 * <p>OMS의 익스체인지도 함께 선언한다. 익스체인지 선언은 멱등이라 발행자가 먼저 만들었어도
 * 문제가 되지 않고, 이렇게 해두면 <b>기동 순서에 의존하지 않는다</b> — OMS보다 먼저 떠도
 * 큐가 만들어져 그동안의 이벤트를 놓치지 않는다.
 *
 * <p>기존 {@code RefundMessagingConfig}(order 발행분)와 별개다. 두 흐름은 발행자와 페이로드가
 * 달라 큐를 공유할 수 없다.
 */
@Configuration
@EnableConfigurationProperties(OmsRefundMessagingProperties.class)
public class OmsRefundMessagingConfig {

    @Bean
    public TopicExchange omsTopicExchange(OmsRefundMessagingProperties properties) {
        return ExchangeBuilder.topicExchange(properties.exchange()).durable(true).build();
    }

    /**
     * 처리할 수 없는 메시지가 가는 곳.
     *
     * <p>없으면 그런 메시지가 무한히 재배달되며 큐를 막는다. 환불은 돈이 걸린 흐름이라
     * 막히면 다른 고객의 환불까지 지연된다.
     */
    @Bean
    public Queue omsRefundDeadLetterQueue(OmsRefundMessagingProperties properties) {
        return QueueBuilder.durable(properties.deadLetter()).build();
    }

    @Bean
    public Queue omsRefundQueue(OmsRefundMessagingProperties properties) {
        return QueueBuilder.durable(properties.queue())
                // 기본 익스체인지로 보내면 라우팅 키가 곧 큐 이름이 된다.
                .deadLetterExchange("")
                .deadLetterRoutingKey(properties.deadLetter())
                .build();
    }

    @Bean
    public Binding omsRefundBinding(Queue omsRefundQueue, TopicExchange omsTopicExchange,
                                    OmsRefundMessagingProperties properties) {
        return BindingBuilder.bind(omsRefundQueue).to(omsTopicExchange).with(properties.routingKey());
    }
}
