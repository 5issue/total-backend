package com.kurly.product.infrastructure.config;

import com.kurly.product.infrastructure.messaging.WmsEventMessagingProperties;
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
@EnableConfigurationProperties(WmsEventMessagingProperties.class)
public class WmsEventMessagingConfig {

    @Bean
    public SimpleRabbitListenerContainerFactory wmsEventListenerContainerFactory(ConnectionFactory connectionFactory,
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
    public TopicExchange wmsTopicExchange(WmsEventMessagingProperties properties) {
        return ExchangeBuilder.topicExchange(properties.exchange()).durable(true).build();
    }

    @Bean
    public Queue wmsEventDeadLetterQueue(WmsEventMessagingProperties properties) {
        return QueueBuilder.durable(properties.deadLetter()).build();
    }

    @Bean
    public Queue inboundCompletedQueue(WmsEventMessagingProperties properties) {
        return buildQueue(properties.inboundCompletedQueue(), properties.deadLetter());
    }

    @Bean
    public Queue outboundCompletedQueue(WmsEventMessagingProperties properties) {
        return buildQueue(properties.outboundCompletedQueue(), properties.deadLetter());
    }

    @Bean
    public Binding inboundCompletedBinding(Queue inboundCompletedQueue, TopicExchange wmsTopicExchange,
                                            WmsEventMessagingProperties properties) {
        return BindingBuilder.bind(inboundCompletedQueue).to(wmsTopicExchange).with(properties.inboundCompletedRoutingKey());
    }

    @Bean
    public Binding outboundCompletedBinding(Queue outboundCompletedQueue, TopicExchange wmsTopicExchange,
                                             WmsEventMessagingProperties properties) {
        return BindingBuilder.bind(outboundCompletedQueue).to(wmsTopicExchange).with(properties.outboundCompletedRoutingKey());
    }

    private Queue buildQueue(String name, String deadLetter) {
        return QueueBuilder.durable(name)
                .deadLetterExchange("")
                .deadLetterRoutingKey(deadLetter)
                .build();
    }
}
