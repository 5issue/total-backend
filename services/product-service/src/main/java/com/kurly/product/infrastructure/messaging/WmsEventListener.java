package com.kurly.product.infrastructure.messaging;

import com.kurly.common.exception.BusinessException;
import com.kurly.product.application.WmsEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * WMS 입고/출고 완료 이벤트 소비자.
 *
 * <p>{@link BusinessException}은 재시도해도 같은 결과인 데이터/상태 문제라 DLQ로 보낸다.
 * 그 밖의 예외는 그대로 올려보내 브로커가 재배달하게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WmsEventListener {

    private final WmsEventService wmsEventService;

    @RabbitListener(queues = "${product.wms-event.inbound-completed-queue}", containerFactory = "wmsEventListenerContainerFactory")
    public void onInboundCompleted(InboundCompletedEvent event) {
        try {
            wmsEventService.handleInboundCompleted(event.eventId(), event.productId(), event.quantity());
        } catch (BusinessException e) {
            log.error("입고 완료 처리 실패. DLQ로 보낸다: eventId={}, productId={}", event.eventId(), event.productId(), e);
            throw new AmqpRejectAndDontRequeueException(
                    "입고 완료 처리 실패: productId=" + event.productId(), e);
        }
    }

    @RabbitListener(queues = "${product.wms-event.outbound-completed-queue}", containerFactory = "wmsEventListenerContainerFactory")
    public void onOutboundCompleted(OutboundCompletedEvent event) {
        try {
            wmsEventService.handleOutboundCompleted(event.eventId(), event.orderId(), event.items());
        } catch (BusinessException e) {
            log.error("출고 완료 처리 실패. DLQ로 보낸다: eventId={}, orderId={}", event.eventId(), event.orderId(), e);
            throw new AmqpRejectAndDontRequeueException(
                    "출고 완료 처리 실패: orderId=" + event.orderId(), e);
        }
    }
}
