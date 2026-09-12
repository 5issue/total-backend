package com.kurly.order.infrastructure.messaging;

import com.kurly.order.domain.order.OrderEvent;
import com.kurly.order.domain.order.SalesOrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @ApplicationModuleListener
    public void publish(OrderEvent event) {
        rabbitTemplate.convertAndSend(OrderRabbitMqConfig.EXCHANGE, event.routingKey(), event);
    }

    @ApplicationModuleListener
    public void publishSalesOrderCreated(SalesOrderCreatedEvent event) {
        rabbitTemplate.convertAndSend(OrderRabbitMqConfig.EXCHANGE, event.routingKey(), event);
    }
}
