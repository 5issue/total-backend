package com.kurly.product.infrastructure.messaging;

import com.kurly.common.exception.BusinessException;
import com.kurly.product.application.ProductInventoryService;
import com.kurly.product.domain.exception.ProductException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 주문 재고 선점/해제/확정 이벤트 소비자.
 *
 * <p>{@link BusinessException}은 재시도해도 같은 결과인 데이터/상태 문제라 DLQ로 보낸다.
 * 그 밖의 예외는 그대로 올려보내 브로커가 재배달하게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventListener {

    private final ProductInventoryService productInventoryService;

    @RabbitListener(queues = "${product.inventory.release-queue}")
    public void onRelease(InventoryEvent event) {
        try {
            productInventoryService.release(event.reservationToken());
        } catch (BusinessException e) {
            log.error("재고 선점 해제 실패. DLQ로 보낸다: eventId={}, orderId={}", event.eventId(), event.orderId(), e);
            throw new AmqpRejectAndDontRequeueException("재고 선점 해제 실패: orderId=" + event.orderId(), e);
        }
    }

    @RabbitListener(queues = "${product.inventory.confirm-queue}")
    public void onConfirm(InventoryEvent event) {
        try {
            productInventoryService.confirm(event.orderId(), event.items());
        } catch (ProductException e) {
            log.info("재고 확정 실패(재고 부족)로 종료. eventId={}, orderId={}", event.eventId(), event.orderId());
        } catch (Exception e) {
            log.error("재고 선점 확정 실패. DLQ로 보낸다: eventId={}, orderId={}", event.eventId(), event.orderId(), e);
            throw new AmqpRejectAndDontRequeueException("재고 선점 확정 실패: orderId=" + event.orderId(), e);
        }
    }

    @RabbitListener(queues = "${product.inventory.restore-queue}")
    public void onRestore(InventoryEvent event) {
        try {
            productInventoryService.restore(event.orderId(), event.items());
        } catch (Exception e) {
            log.error("재고 복구 실패. DLQ로 보낸다: eventId={}, orderId={}", event.eventId(), event.orderId(), e);
            throw new AmqpRejectAndDontRequeueException("재고 복구 실패: orderId=" + event.orderId(), e);
        }
    }
}
