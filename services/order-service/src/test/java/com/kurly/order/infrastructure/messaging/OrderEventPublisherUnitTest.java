package com.kurly.order.infrastructure.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderEventPublisherUnitTest {
    @Test
    void 재고_이벤트를_Product가_구독하는_경로로_발행한다() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        OrderEventPublisher publisher = new OrderEventPublisher(rabbitTemplate);
        UUID eventId = UUID.randomUUID();
        UUID reservationToken = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        var restore = new OrderInventoryRestoreEvent(eventId, reservationToken, 1L, 2L, List.of(), now);
        var release = new OrderInventoryReleaseEvent(eventId, reservationToken, 1L, 2L, List.of(), now);
        var confirm = new OrderInventoryConfirmEvent(eventId, reservationToken, 1L, 2L, List.of(), now);

        publisher.publishInventoryRestore(restore);
        publisher.publishInventoryRelease(release);
        publisher.publishInventoryConfirm(confirm);

        verify(rabbitTemplate).convertAndSend(OrderRabbitMqConfig.EXCHANGE_ORDER,
                OrderRabbitMqConfig.ROUTING_KEY_INVENTORY_RESTORE, restore);
        verify(rabbitTemplate).convertAndSend(OrderRabbitMqConfig.EXCHANGE_ORDER,
                OrderRabbitMqConfig.ROUTING_KEY_INVENTORY_RELEASE, release);
        verify(rabbitTemplate).convertAndSend(OrderRabbitMqConfig.EXCHANGE_ORDER,
                OrderRabbitMqConfig.ROUTING_KEY_INVENTORY_CONFIRM, confirm);
    }
}
