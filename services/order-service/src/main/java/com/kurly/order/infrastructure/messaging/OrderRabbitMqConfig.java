package com.kurly.order.infrastructure.messaging;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderRabbitMqConfig {

    public static final String EXCHANGE = "order.topic.exchange";

    @Bean
    TopicExchange orderTopicExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }
}
