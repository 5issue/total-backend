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
    public static final String EXCHANGE_PAYMENT = "payment.topic.exchange";

    // ==========================================
    // Routing Keys
    // ==========================================
    public static final String ROUTING_KEY_ORDER_PAYMENT_COMPLETED = "order.payment.completed";
    public static final String ROUTING_KEY_ORDER_RETURN_REQUESTED = "order.return-requested";

    public static final String ROUTING_KEY_REFUND_REQUESTED = "oms.order-refund.requested";
    public static final String ROUTING_KEY_INSPECTION_REQUESTED = "oms.return.inspection-requested";

    public static final String ROUTING_KEY_WMS_INSPECTED = "wms.return.inspected";

    public static final String ROUTING_KEY_PAYMENT_REFUNDED = "payment.refund.completed";


    // ==========================================
    // Queues & DLQs
    // ==========================================
    public static final String QUEUE_PAYMENT_COMPLETED = "oms.order-payment-completed.queue";
    public static final String DLQ_PAYMENT_COMPLETED = "oms.order-payment-completed.dlq";

    public static final String QUEUE_RETURN_REQUESTED = "oms.return-requested.queue";
    public static final String DLQ_RETURN_REQUESTED = "oms.return-requested.dlq";

    public static final String QUEUE_WMS_INSPECTED = "oms.wms-inspected.queue";
    public static final String DLQ_WMS_INSPECTED = "oms.wms-inspected.dlq";

    public static final String QUEUE_PAYMENT_REFUNDED = "oms.payment-refunded.queue";
    public static final String DLQ_PAYMENT_REFUNDED = "oms.payment-refunded.dlq";

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
        return new Binding(QUEUE_PAYMENT_COMPLETED, Binding.DestinationType.QUEUE, EXCHANGE_ORDER, ROUTING_KEY_ORDER_PAYMENT_COMPLETED, null);
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
        return new Binding(QUEUE_RETURN_REQUESTED, Binding.DestinationType.QUEUE, EXCHANGE_ORDER, ROUTING_KEY_ORDER_RETURN_REQUESTED, null);
    }

    // ==========================================
    // 3. WMS Inspected Flow
    // ==========================================
    @Bean
    Queue wmsInspectedDlq() {
        return QueueBuilder.durable(DLQ_WMS_INSPECTED).build();
    }

    @Bean
    Queue wmsInspectedQueue() {
        return QueueBuilder.durable(QUEUE_WMS_INSPECTED)
                .deadLetterExchange("")
                .deadLetterRoutingKey(DLQ_WMS_INSPECTED)
                .build();
    }

    @Bean
    Binding wmsInspectedBinding() {
        return new Binding(QUEUE_WMS_INSPECTED, Binding.DestinationType.QUEUE, EXCHANGE_OMS, ROUTING_KEY_WMS_INSPECTED, null);
    }

    // ==========================================
    // 3. Payment Refund Completed Flow
    // ==========================================
    @Bean
    Queue paymentRefundedDlq() {
        return QueueBuilder.durable(DLQ_PAYMENT_REFUNDED).build();
    }

    @Bean
    Queue paymentRefundedQueue() {
        return QueueBuilder.durable(QUEUE_PAYMENT_REFUNDED)
                .deadLetterExchange("")
                .deadLetterRoutingKey(DLQ_PAYMENT_REFUNDED)
                .build();
    }

    @Bean
    Binding paymentRefundedBinding() {
        return new Binding(QUEUE_PAYMENT_REFUNDED, Binding.DestinationType.QUEUE, EXCHANGE_PAYMENT, ROUTING_KEY_PAYMENT_REFUNDED, null);
    }
}