package com.kurly.wms.infrastructure.jpa;

import com.kurly.wms.infrastructure.entity.InboundItem;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface InboundItemJpaRepository extends JpaRepository<InboundItem, Long> {
    List<InboundItem> findByInboundOrderId(Long inboundOrderId);

    /**
     * ObjectOptimisticLockingFailureException으로 실패시킨다(GlobalExceptionHandler가 409로 변환).
     */
    @Lock(LockModeType.OPTIMISTIC)
    Optional<InboundItem> findWithOptimisticLockById(Long id);
}
