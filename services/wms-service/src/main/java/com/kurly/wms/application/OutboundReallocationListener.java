package com.kurly.wms.application;

import com.kurly.wms.application.event.OutboundAllocatedEvent;
import com.kurly.wms.application.event.PutAwayCompletedEvent;
import com.kurly.wms.application.event.ReplenishmentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 출고 전표(OutboundOrder) 상태 변화에 반응하는 리스너 모음. ①물리 이동 완료 신호를 받아 막혀
 * 있던 출고 할당을 이어가는 것(재시도/최종 할당), ②전표가 ALLOCATED 됐을 때 피킹 Task를
 * 자동 생성하는 것 둘 다 다룬다. 전부 원래 트랜잭션이 커밋된 뒤 별도 트랜잭션으로 실행돼야(재시도/
 * 최종 할당/Task 생성이 참조하는 상태가 실제로 반영된 뒤라야 함) 하고, 실패해도 원래 요청의
 * 응답에 영향을 주면 안 되므로 예외를 삼킨다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboundReallocationListener {

    private final OutboundOrderService outboundOrderService;
    private final TaskService taskService;

    /** 보관존 재고가 늘었으니, 보충 예약조차 못했던(UNALLOCATED) 품목을 재시도한다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPutAwayCompleted(PutAwayCompletedEvent event) {
        try {
            outboundOrderService.retryUnallocated(event.warehouseId(), event.productId());
        } catch (RuntimeException e) {
            log.error("PutAwayCompletedEvent 처리(UNALLOCATED 재시도) 실패. warehouseId={}, productId={}",
                    event.warehouseId(), event.productId(), e);
        }
    }

    /** 예약해둔 물량이 실제로 피킹존에 도착했으니, 대기 중인(PENDING_REPLENISHMENT) 품목을 최종 할당한다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReplenishmentCompleted(ReplenishmentCompletedEvent event) {
        try {
            outboundOrderService.finalizeReplenishment(event.warehouseId(), event.productId(),
                    event.locationId(), event.lotNo(), event.expiredDate(), event.quantity());
        } catch (RuntimeException e) {
            log.error("ReplenishmentCompletedEvent 처리(최종 할당) 실패. warehouseId={}, productId={}",
                    event.warehouseId(), event.productId(), e);
        }
    }

    /** 전표가 ALLOCATED 됐으니, 포함된 품목마다 피킹 Task를 자동 생성한다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutboundAllocated(OutboundAllocatedEvent event) {
        try {
            taskService.createPickingTasks(event.outboundOrderId());
        } catch (RuntimeException e) {
            log.error("OutboundAllocatedEvent 처리(피킹 Task 생성) 실패. outboundOrderId={}", event.outboundOrderId(), e);
        }
    }
}
