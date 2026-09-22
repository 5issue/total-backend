package com.kurly.oms.infrastructure.messaging;

import com.kurly.oms.application.OmsOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OmsOrderEventHandler {

    private final OmsOrderService omsOrderService;

    @RabbitListener(queues = OmsRabbitMqConfig.QUEUE_PAYMENT_COMPLETED)
    public void handle(OrderPaymentCompletedMessage event) {
        log.info("[OmsOrderEventHandler] 수신 이벤트 orderId={}, eventId={}", event.orderId(), event.eventId());
        omsOrderService.createOrder(event);
    }

}
