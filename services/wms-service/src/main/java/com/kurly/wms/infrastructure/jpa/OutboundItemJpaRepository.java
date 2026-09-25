package com.kurly.wms.infrastructure.jpa;

import com.kurly.wms.infrastructure.entity.OutboundItem;
import com.kurly.wms.infrastructure.entity.OutboundItem.OutboundItemStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboundItemJpaRepository extends JpaRepository<OutboundItem, Long> {
    /** 전표 실패 처리 시 상태와 무관하게 모든 품목(ALLOCATED 재고 해제, PENDING_REPLENISHMENT 정리 포함)을 훑는 데 쓴다. */
    List<OutboundItem> findByOutboundOrderId(Long outboundOrderId);

    /**
     * PutAwayCompletedEvent(재시도 대상: UNALLOCATED)/ReplenishmentCompletedEvent(최종 할당
     * 대상: PENDING_REPLENISHMENT) 양쪽 소비자가 공용으로 쓰는 조회. 여러 전표가 같은 상품을
     * 동시에 기다리고 있을 때 먼저 들어온 주문부터 처리하도록(FIFO) 전표 생성 시각 오름차순으로
     * 정렬한다. 동시에 완료된 이동 이벤트가 같은 대기 품목을 이중 할당하지 않도록 비관적 락을 건다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT oi FROM OutboundItem oi
            JOIN oi.outboundOrder oo
            WHERE oo.warehouse.id = :warehouseId
              AND oi.product.id = :productId
              AND oi.status = :status
            ORDER BY oo.createdAt ASC, oi.id ASC
            """)
    List<OutboundItem> findByWarehouseIdAndProductIdAndStatusOrderByOutboundOrderCreatedAtAsc(
            @Param("warehouseId") Long warehouseId,
            @Param("productId") Long productId,
            @Param("status") OutboundItemStatus status);

    /** 재할당 소비자가 전표를 다시 ALLOCATED로 되돌려도 되는지(남은 미완료 품목이 없는지) 확인하는 데 쓴다. */
    boolean existsByOutboundOrderIdAndStatusNot(Long outboundOrderId, OutboundItemStatus status);
}
