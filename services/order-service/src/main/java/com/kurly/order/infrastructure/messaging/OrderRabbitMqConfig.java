package com.kurly.order.infrastructure.messaging;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderRabbitMqConfig {

    // ==========================================
    // Exchanges
    // ==========================================
    public static final String EXCHANGE_ORDER = "order.topic.exchange";
    public static final String EXCHANGE_PRODUCT = "product.topic.exchange";

    // ==========================================
    // Routing Keys
    // ==========================================
    public static final String ROUTING_KEY_ORDER_PAYMENT_COMPLETED = "order.payment.completed";
    public static final String ROUTING_KEY_ORDER_RETURN_REQUESTED = "order.return-requested";

    public static final String ROUTING_KEY_INVENTORY_RESTORE = "order.inventory.restore";
    public static final String ROUTING_KEY_INVENTORY_RELEASE = "order.inventory.release";
    public static final String ROUTING_KEY_INVENTORY_CONFIRM = "order.inventory.confirm";

    public static final String ROUTING_KEY_INVENTORY_RESTORED = "product.inventory.restored";
    public static final String ROUTING_KEY_INVENTORY_CONFIRMED = "product.inventory.confirmed";


    // ==========================================
    // Queues & DLQs
    // ==========================================
    public static final String QUEUE_INVENTORY_RESTORED = "order.inventory-restored.queue";
    public static final String DLQ_INVENTORY_RESTORED = "order.inventory-restored.dlq";

    public static final String QUEUE_INVENTORY_CONFIRMED = "order.inventory-confirmed.queue";
    public static final String DLQ_INVENTORY_CONFIRMED = "order.inventory-confirmed.dlq";

    // ==========================================
    // Exchange Beans
    // ==========================================
    @Bean
    TopicExchange orderTopicExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_ORDER).durable(true).build();
    }

    @Bean
    TopicExchange productTopicExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_PRODUCT).durable(true).build();
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


    // ==========================================
    // Inventory Confirmed Flow
    // ==========================================
    @Bean
    Queue inventoryConfirmedDlq() {
        return QueueBuilder.durable(DLQ_INVENTORY_CONFIRMED).build();
    }

    @Bean
    Queue inventoryConfirmedQueue() {
        return QueueBuilder.durable(QUEUE_INVENTORY_CONFIRMED)
                .deadLetterExchange("")
                .deadLetterRoutingKey(DLQ_INVENTORY_CONFIRMED)
                .build();
    }

    @Bean
    Binding inventoryConfirmedBinding() {
        return new Binding(
                QUEUE_INVENTORY_CONFIRMED,
                Binding.DestinationType.QUEUE,
                EXCHANGE_PRODUCT,
                ROUTING_KEY_INVENTORY_CONFIRMED,
                null
        );
    }
}
