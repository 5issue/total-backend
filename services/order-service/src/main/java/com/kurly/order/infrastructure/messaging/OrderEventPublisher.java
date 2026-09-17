package com.kurly.order.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @ApplicationModuleListener
    public void publishOrderPaymentCompleted(OrderPaymentCompletedEvent event) {
        rabbitTemplate.convertAndSend(
                OrderRabbitMqConfig.EXCHANGE_ORDER,
                OrderRabbitMqConfig.ROUTING_KEY_ORDER_PAYMENT_COMPLETED,
                event
        );
    }

    @ApplicationModuleListener
    public void publishOrderReturnRequested(OrderReturnRequestedEvent event) {
        rabbitTemplate.convertAndSend(
                OrderRabbitMqConfig.EXCHANGE_ORDER,
                OrderRabbitMqConfig.ROUTING_KEY_ORDER_RETURN_REQUESTED,
                event
        );
    }
}
