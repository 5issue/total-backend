package com.kurly.wms.infrastructure.jpa;

import com.kurly.wms.infrastructure.entity.StockMovement;
import com.kurly.wms.infrastructure.entity.StockMovement.MovementStatus;
import com.kurly.wms.infrastructure.entity.StockMovement.MovementType;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementJpaRepository extends JpaRepository<StockMovement, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StockMovement> findWithPessimisticLockById(Long id);

    /** warehouseId/movementType/status는 전부 선택 조건이다 — null이면 그 조건은 건너뛴다. */
    @Query("""
            SELECT sm FROM StockMovement sm
            WHERE (:warehouseId IS NULL OR sm.warehouse.id = :warehouseId)
              AND (:movementType IS NULL OR sm.movementType = :movementType)
              AND (:status IS NULL OR sm.status = :status)
            ORDER BY sm.createdAt ASC
            """)
    List<StockMovement> search(
            @Param("warehouseId") Long warehouseId,
            @Param("movementType") MovementType movementType,
            @Param("status") MovementStatus status,
            Pageable pageable);

    boolean existsByToLocationIdAndStatusIn(Long id, List<MovementStatus> pending);
}
