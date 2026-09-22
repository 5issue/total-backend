package com.kurly.wms.infrastructure.config;

import com.kurly.wms.infrastructure.messaging.OrderMessagingProperties;
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

/**
 * order-service의 {@code order.inventory.confirm} 구독 설정. 익스체인지는 order-service가
 * 이미 선언해 두지만, 큐/바인딩은 소비자인 WMS가 직접 선언한다(product-service와 동일 패턴).
 */
@Configuration
@EnableConfigurationProperties(OrderMessagingProperties.class)
public class OrderMessagingConfig {

    @Bean
    public SimpleRabbitListenerContainerFactory orderListenerContainerFactory(ConnectionFactory connectionFactory,
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
    public TopicExchange orderTopicExchange(OrderMessagingProperties properties) {
        return ExchangeBuilder.topicExchange(properties.exchange()).durable(true).build();
    }

    @Bean
    public Queue orderInventoryConfirmDeadLetterQueue(OrderMessagingProperties properties) {
        return QueueBuilder.durable(properties.deadLetter()).build();
    }

    @Bean
    public Queue orderInventoryConfirmQueue(OrderMessagingProperties properties) {
        return QueueBuilder.durable(properties.confirmQueue())
                .deadLetterExchange("")
                .deadLetterRoutingKey(properties.deadLetter())
                .build();
    }

    @Bean
    public Binding orderInventoryConfirmBinding(Queue orderInventoryConfirmQueue, TopicExchange orderTopicExchange,
                                                 OrderMessagingProperties properties) {
        return BindingBuilder.bind(orderInventoryConfirmQueue).to(orderTopicExchange).with(properties.confirmRoutingKey());
    }
}
