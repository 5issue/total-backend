package com.kurly.order.infrastructure.messaging;

import com.kurly.order.application.OrderExternalService;
import com.kurly.order.domain.order.OrderEvent;
import com.kurly.order.domain.order.PaymentCancellationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class PaymentCancellationHandlerUnitTest {

    @Mock OrderExternalService externalService;
    @Mock RabbitTemplate rabbitTemplate;

    @Test
    void 결제_취소_성공_후에만_재고_복구_이벤트를_발행한다() {
        OrderEvent restoreEvent = new OrderEvent(UUID.randomUUID(), "order.canceled.inventory-restore",
                "rsv_test", 1L, 2L, List.of(), LocalDateTime.now());
        PaymentCancellationHandler handler = new PaymentCancellationHandler(externalService, rabbitTemplate);

        handler.cancel(new PaymentCancellationEvent(3L, restoreEvent));

        InOrder order = inOrder(externalService, rabbitTemplate);
        order.verify(externalService).cancelPayment(3L);
        order.verify(rabbitTemplate).convertAndSend(OrderRabbitMqConfig.EXCHANGE,
                "order.canceled.inventory-restore", restoreEvent);
    }
}
