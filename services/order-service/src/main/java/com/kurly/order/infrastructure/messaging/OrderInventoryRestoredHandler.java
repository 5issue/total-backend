package com.kurly.order.infrastructure.messaging;

import com.kurly.order.application.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderInventoryRestoredHandler {

    private final OrderService orderService;

    @RabbitListener(queues = OrderRabbitMqConfig.INVENTORY_RESTORED_QUEUE)
    public void handle(ProductInventoryRestoredEvent event) {
        log.info("상품 재고 원복 완료 수신: orderId={}, eventId={}", event.orderId(), event.eventId());
        orderService.completeCancel(event.orderId());
    }
}