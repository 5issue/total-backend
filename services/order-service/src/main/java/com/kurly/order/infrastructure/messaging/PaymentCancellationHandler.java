package com.kurly.order.infrastructure.messaging;

import com.kurly.order.application.OrderExternalService;
import com.kurly.order.domain.order.PaymentCancellationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentCancellationHandler {

    private final OrderExternalService externalService;
    private final RabbitTemplate rabbitTemplate;

    @ApplicationModuleListener
    public void cancel(PaymentCancellationEvent event) {
        externalService.cancelPayment(event.paymentId());
        rabbitTemplate.convertAndSend(OrderRabbitMqConfig.EXCHANGE,
                event.inventoryRestoreEvent().routingKey(), event.inventoryRestoreEvent());
    }
}
