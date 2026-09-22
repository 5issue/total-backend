package com.kurly.wms.application;

import com.kurly.wms.application.event.PutAwayCompletedEvent;
import com.kurly.wms.application.event.ReplenishmentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 물리 이동 완료 신호를 받아 막혀 있던 출고 할당을 이어간다. 원래 트랜잭션(StockMovement 확정)이
 * 커밋된 뒤에 별도 트랜잭션으로 실행돼야(재시도/최종 할당이 참조하는 재고 변경이 실제로 반영된
 * 뒤라야 함) 하고, 실패해도 원래 확정 요청의 응답에 영향을 주면 안 되므로 예외를 삼킨다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboundReallocationListener {

    private final OutboundOrderService outboundOrderService;

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
}
