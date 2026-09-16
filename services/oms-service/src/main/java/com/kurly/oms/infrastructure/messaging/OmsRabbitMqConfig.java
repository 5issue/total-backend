package com.kurly.oms.infrastructure.messaging;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
public class OmsRabbitMqConfig {

    public static final String ORDER_EXCHANGE = "order.topic.exchange";
    public static final String OMS_EXCHANGE = "oms.topic.exchange";
    public static final String RETURN_REQUESTED_QUEUE = "oms.return-requested.queue";
    public static final String RETURN_REQUESTED_DLQ = "oms.return-requested.dlq";
    public static final String RETURN_REQUESTED_KEY = "order.return-requested";

    @Bean
    TopicExchange orderTopicExchange() {
        return ExchangeBuilder.topicExchange(ORDER_EXCHANGE).durable(true).build();
    }

    @Bean
    TopicExchange omsTopicExchange() {
        return ExchangeBuilder.topicExchange(OMS_EXCHANGE).durable(true).build();
    }

    @Bean
    Queue returnRequestedDeadLetterQueue() {
        return QueueBuilder.durable(RETURN_REQUESTED_DLQ).build();
    }

    @Bean
    Queue returnRequestedQueue() {
        return QueueBuilder.durable(RETURN_REQUESTED_QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(RETURN_REQUESTED_DLQ)
                .build();
    }

    @Bean
    Binding returnRequestedBinding(@Qualifier("returnRequestedQueue") Queue returnRequestedQueue,
                                   @Qualifier("orderTopicExchange") TopicExchange orderTopicExchange) {
        return BindingBuilder.bind(returnRequestedQueue).to(orderTopicExchange).with(RETURN_REQUESTED_KEY);
    }
}
