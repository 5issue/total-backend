package com.kurly.order.infrastructure.messaging;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderRabbitMqConfig {

    public static final String EXCHANGE = "order.topic.exchange";
    public static final String PRODUCT_EXCHANGE = "product.topic.exchange";
    public static final String INVENTORY_RESTORED_QUEUE = "order.inventory-restored.queue";
    public static final String ROUTING_KEY_INVENTORY_RESTORED = "product.inventory.restored";


    // ==========================================
    // Queues & DLQs
    // ==========================================
    public static final String QUEUE_INVENTORY_RESTORED = "order.inventory-restored.queue";
    public static final String DLQ_INVENTORY_RESTORED = "order.inventory-restored.dlq";

    // ==========================================
    // Exchange Beans
    // ==========================================
    @Bean
    TopicExchange orderTopicExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_ORDER).durable(true).build();
    }

    // ==========================================
    // Inventory Restored Flow
    // ==========================================
    @Bean
    Queue inventoryRestoredDlq() {
        return QueueBuilder.durable(DLQ_INVENTORY_RESTORED).build();
    }

    @Bean
    Queue inventoryRestoredQueue() {
        return QueueBuilder.durable(QUEUE_INVENTORY_RESTORED)
                .deadLetterExchange("")
                .deadLetterRoutingKey(DLQ_INVENTORY_RESTORED)
                .build();
    }

    @Bean
    Binding inventoryRestoredBinding() {
        return new Binding(
                QUEUE_INVENTORY_RESTORED,
                Binding.DestinationType.QUEUE,
                EXCHANGE_PRODUCT,
                ROUTING_KEY_INVENTORY_RESTORED,
                null
        );
    }
}