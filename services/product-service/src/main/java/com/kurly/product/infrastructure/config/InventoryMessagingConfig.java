package com.kurly.product.infrastructure.config;

import com.kurly.product.infrastructure.messaging.InventoryEvent;
import com.kurly.product.infrastructure.messaging.InventoryMessagingProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(InventoryMessagingProperties.class)
public class InventoryMessagingConfig {

    @Bean
    public SimpleRabbitListenerContainerFactory inventoryListenerContainerFactory(ConnectionFactory connectionFactory,
                                                                                    MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);

        factory.setAdviceChain(
                RetryInterceptorBuilder.stateless()
                        .maxRetries(3)
                        .backOffOptions(1000, 2.0, 5000)
                        .recoverer(new RejectAndDontRequeueRecoverer())
                        .build()
        );
        return factory;
    }

    @Bean
    public TopicExchange orderTopicExchange(InventoryMessagingProperties properties) {
        return ExchangeBuilder.topicExchange(properties.exchange()).durable(true).build();
    }

    @Bean
    public Queue inventoryDeadLetterQueue(InventoryMessagingProperties properties) {
        return QueueBuilder.durable(properties.deadLetter()).build();
    }

    @Bean
    public Queue inventoryReleaseQueue(InventoryMessagingProperties properties) {
        return buildQueue(properties.releaseQueue(), properties.deadLetter());
    }

    @Bean
    public Queue inventoryConfirmQueue(InventoryMessagingProperties properties) {
        return buildQueue(properties.confirmQueue(), properties.deadLetter());
    }

    @Bean
    public Queue inventoryRestoreQueue(InventoryMessagingProperties properties) {
        return buildQueue(properties.restoreQueue(), properties.deadLetter());
    }

    @Bean
    public Binding inventoryReleaseBinding(Queue inventoryReleaseQueue, TopicExchange orderTopicExchange,
                                            InventoryMessagingProperties properties) {
        return BindingBuilder.bind(inventoryReleaseQueue).to(orderTopicExchange).with(properties.releaseRoutingKey());
    }

    @Bean
    public Binding inventoryConfirmBinding(Queue inventoryConfirmQueue, TopicExchange orderTopicExchange,
                                            InventoryMessagingProperties properties) {
        return BindingBuilder.bind(inventoryConfirmQueue).to(orderTopicExchange).with(properties.confirmRoutingKey());
    }

    @Bean
    public Binding inventoryRestoreBinding(Queue inventoryRestoreQueue, TopicExchange orderTopicExchange,
                                            InventoryMessagingProperties properties) {
        return BindingBuilder.bind(inventoryRestoreQueue).to(orderTopicExchange).with(properties.restoreRoutingKey());
    }

    private Queue buildQueue(String name, String deadLetter) {
        return QueueBuilder.durable(name)
                .deadLetterExchange("")
                .deadLetterRoutingKey(deadLetter)
                .build();
    }
}
