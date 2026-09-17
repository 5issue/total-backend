package com.kurly.oms.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OmsEventPublisher {

    private final RabbitTemplate rabbitTemplate;

//    @ApplicationModuleListener
//    public void publishRefundRequested(RefundRequestedEvent event) {
//        rabbitTemplate.convertAndSend(OmsRabbitMqConfig.ORDER_EXCHANGE, event.routingKey(), event);
//    }
//
//    @ApplicationModuleListener
//    public void publishInspectionRequested(ReturnInspectionRequestedEvent event) {
//        rabbitTemplate.convertAndSend(OmsRabbitMqConfig.OMS_EXCHANGE, event.routingKey(), event);
//    }
}
