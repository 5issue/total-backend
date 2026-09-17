package com.kurly.oms.infrastructure.messaging;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OmsRabbitMqConfig {

    // ==========================================
    // Exchanges
    // ==========================================
    public static final String EXCHANGE_ORDER = "order.topic.exchange";
    public static final String EXCHANGE_OMS = "oms.topic.exchange";

    // ==========================================
    // Routing Keys
    // ==========================================
    public static final String ROUTING_KEY_ORDER_PAYMENT_COMPLETED = "order.payment.completed";
    public static final String ROUTING_KEY_ORDER_RETURN_REQUESTED = "order.return-requested";

    // ==========================================
    // Queues & DLQs
    // ==========================================
    public static final String QUEUE_PAYMENT_COMPLETED = "oms.order-payment-completed.queue";
    public static final String DLQ_PAYMENT_COMPLETED = "oms.order-payment-completed.dlq";

    public static final String QUEUE_RETURN_REQUESTED = "oms.return-requested.queue";
    public static final String DLQ_RETURN_REQUESTED = "oms.return-requested.dlq";

    // ==========================================
    // Exchange Beans
    // ==========================================
    @Bean
    TopicExchange omsTopicExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_OMS).durable(true).build();
    }

    // ==========================================
    // 1. Payment Completed Flow
    // ==========================================
    @Bean
    Queue paymentCompletedDlq() {
        return QueueBuilder.durable(DLQ_PAYMENT_COMPLETED).build();
    }

    @Bean
    Queue paymentCompletedQueue() {
        return QueueBuilder.durable(QUEUE_PAYMENT_COMPLETED)
                .deadLetterExchange("")
                .deadLetterRoutingKey(DLQ_PAYMENT_COMPLETED)
                .build();
    }

    @Bean
    Binding paymentCompletedBinding() {
        return new Binding(
                QUEUE_PAYMENT_COMPLETED,
                Binding.DestinationType.QUEUE,
                EXCHANGE_ORDER,
                ROUTING_KEY_ORDER_PAYMENT_COMPLETED,
                null
        );
    }

    // ==========================================
    // 2. Return Requested Flow
    // ==========================================
    @Bean
    Queue returnRequestedDlq() {
        return QueueBuilder.durable(DLQ_RETURN_REQUESTED).build();
    }

    @Bean
    Queue returnRequestedQueue() {
        return QueueBuilder.durable(QUEUE_RETURN_REQUESTED)
                .deadLetterExchange("")
                .deadLetterRoutingKey(DLQ_RETURN_REQUESTED)
                .build();
    }

    @Bean
    Binding returnRequestedBinding() {
        return new Binding(
                QUEUE_RETURN_REQUESTED,
                Binding.DestinationType.QUEUE,
                EXCHANGE_ORDER,
                ROUTING_KEY_ORDER_RETURN_REQUESTED,
                null
        );
    }
}