package com.kurly.wms.infrastructure.jpa;

import com.kurly.wms.infrastructure.entity.OutboundItem.OutboundItemStatus;
import com.kurly.wms.infrastructure.entity.OutboundOrder;
import com.kurly.wms.infrastructure.entity.OutboundOrder.OutboundOrderStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboundOrderJpaRepository extends JpaRepository<OutboundOrder, Long> {
    /** 같은 이벤트가 재배달돼도 출고 지시를 중복 생성하지 않기 위한 멱등성 체크에 쓴다. */
    Optional<OutboundOrder> findByOrderId(Long orderId);

    /** 출고 완료 처리(실물 재고 차감)가 동시에 두 번 실행되지 않도록 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OutboundOrder> findWithPessimisticLockById(Long id);

    /**
     * OutboundOrderFailureScheduler가 주기적으로 실패 처리 대상을 찾는 조회. 품목 중 하나라도
     * unallocatedStatus(UNALLOCATED)로 cutoff 이전부터 머물러 있는 전표를 대상으로 한다 —
     * 보충 지시가 이미 걸린(PENDING_REPLENISHMENT) 전표는 정상 진행 중이라 제외한다.
     */
    @Query("""
            SELECT oo FROM OutboundOrder oo
            WHERE oo.createdAt < :cutoff
              AND oo.status <> :excludedStatus
              AND EXISTS (
                  SELECT 1 FROM OutboundItem oi
                  WHERE oi.outboundOrder = oo AND oi.status = :unallocatedStatus
              )
            """)
    List<OutboundOrder> findTimedOutUnallocated(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("excludedStatus") OutboundOrderStatus excludedStatus,
            @Param("unallocatedStatus") OutboundItemStatus unallocatedStatus);
}
