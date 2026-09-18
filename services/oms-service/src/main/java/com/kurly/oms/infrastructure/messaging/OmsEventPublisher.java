package com.kurly.oms.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OmsEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @ApplicationModuleListener
    public void publishRefundRequested(OmsRefundRequestedEvent event) {
        rabbitTemplate.convertAndSend(
                OmsRabbitMqConfig.EXCHANGE_OMS,
                OmsRabbitMqConfig.ROUTING_KEY_REFUND_REQUESTED,
                event
        );
    }

    @ApplicationModuleListener
    public void publishInspectionRequested(OmsReturnInspectionRequestedEvent event) {
        rabbitTemplate.convertAndSend(
                OmsRabbitMqConfig.EXCHANGE_OMS,
                OmsRabbitMqConfig.ROUTING_KEY_INSPECTION_REQUESTED,
                event
        );
    }
}
