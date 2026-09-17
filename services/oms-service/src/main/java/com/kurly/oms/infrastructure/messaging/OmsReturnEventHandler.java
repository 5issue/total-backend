package com.kurly.oms.infrastructure.messaging;

import com.kurly.oms.application.OmsReturnService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OmsReturnEventHandler {

    private final OmsReturnService returnService;

    @RabbitListener(queues = OmsRabbitMqConfig.QUEUE_RETURN_REQUESTED)
    public void handle(OrderReturnRequestedMessage event) {
        log.info("[OmsReturnEventHandler] 수신 orderId={}, eventId={}", event.orderId(), event.eventId());
        returnService.receiveReturn(event);
    }

}
