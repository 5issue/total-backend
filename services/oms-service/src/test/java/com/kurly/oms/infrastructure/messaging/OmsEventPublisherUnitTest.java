package com.kurly.oms.infrastructure.messaging;

import com.kurly.oms.domain.returnorder.RefundRequestedEvent;
import com.kurly.oms.domain.returnorder.ReturnInspectionRequestedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OmsEventPublisherUnitTest {

    @Mock RabbitTemplate rabbitTemplate;

    @Test
    void 환불_승인_이벤트는_order_exchange로_발행한다() {
        RefundRequestedEvent event = new RefundRequestedEvent(UUID.randomUUID(),
                "order.refund.requested", 1L, 2L, 3L, 4L, 10000L, "RTN02", LocalDateTime.now());

        new OmsEventPublisher(rabbitTemplate).publishRefundRequested(event);

        verify(rabbitTemplate).convertAndSend(OmsRabbitMqConfig.ORDER_EXCHANGE,
                "order.refund.requested", event);
    }

    @Test
    void 검수_요청_이벤트는_oms_exchange로_발행한다() {
        ReturnInspectionRequestedEvent event = new ReturnInspectionRequestedEvent(UUID.randomUUID(),
                "oms.return.inspection-requested", 1L, 2L, LocalDateTime.now());

        new OmsEventPublisher(rabbitTemplate).publishInspectionRequested(event);

        verify(rabbitTemplate).convertAndSend(OmsRabbitMqConfig.OMS_EXCHANGE,
                "oms.return.inspection-requested", event);
    }
}
