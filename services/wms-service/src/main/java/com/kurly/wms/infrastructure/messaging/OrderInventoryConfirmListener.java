package com.kurly.wms.infrastructure.messaging;

import com.kurly.wms.application.OutboundOrderService;
import com.kurly.wms.infrastructure.messaging.dto.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * order-service의 결제 완료(재고 확정) 이벤트를 구독해 출고 지시(OutboundOrder)를 생성한다.
 * 같은 익스체인지의 재고 확정/차감 자체는 product-service가 별도로 구독해 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderInventoryConfirmListener {

    private final OutboundOrderService outboundOrderService;

    @RabbitListener(queues = "${wms.order.confirm-queue}", containerFactory = "orderListenerContainerFactory")
    public void onConfirm(OrderEvent event) {
        try {
            outboundOrderService.createFromOrderEvent(event);
        } catch (Exception e) {
            log.error("출고 지시 생성 실패. DLQ로 보낸다: eventId={}, orderId={}", event.eventId(), event.orderId(), e);
            throw new AmqpRejectAndDontRequeueException("출고 지시 생성 실패: orderId=" + event.orderId(), e);
        }
    }
}
