package com.kurly.order.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
public class OrderRabbitMqConfig {

    public static final String EXCHANGE = "order.topic.exchange";
    public static final String PRODUCT_EXCHANGE = "product.topic.exchange";
    public static final String INVENTORY_RESTORED_QUEUE = "order.inventory-restored.queue";
    public static final String ROUTING_KEY_INVENTORY_RESTORED = "product.inventory.restored";

    @Bean
    TopicExchange orderTopicExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    TopicExchange productTopicExchange() {
        return new TopicExchange(PRODUCT_EXCHANGE, true, false);
    }

    @Bean
    Queue inventoryRestoredQueue() {
        return new Queue(INVENTORY_RESTORED_QUEUE, true);
    }

    @Bean
    Binding inventoryRestoredBinding(Queue inventoryRestoredQueue,
                                     @Qualifier("productTopicExchange") TopicExchange productTopicExchange) {
        return BindingBuilder.bind(inventoryRestoredQueue)
                .to(productTopicExchange)
                .with(ROUTING_KEY_INVENTORY_RESTORED);
    }
}
