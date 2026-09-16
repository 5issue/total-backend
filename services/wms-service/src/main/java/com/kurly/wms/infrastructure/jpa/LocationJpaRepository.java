package com.kurly.wms.infrastructure.jpa;

import com.kurly.wms.domain.enums.StorageType;
import com.kurly.wms.infrastructure.entity.Location;
import com.kurly.wms.infrastructure.entity.Location.LocationStatus;
import com.kurly.wms.infrastructure.entity.Location.LocationType;
import com.kurly.wms.infrastructure.entity.Location.Zone;
import com.kurly.wms.infrastructure.entity.StockMovement.MovementStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LocationJpaRepository extends JpaRepository<Location, Long> {
    Optional<Location> findFirstByWarehouseIdAndLocationTypeAndStatusOrderByIdAsc(
            Long warehouseId, LocationType locationType, LocationStatus status);

    /**
     * 완전히 빈(유효 재고 없음) + 진행 중인 이동 지시로 선점되지 않은 로케이션을
     * aisle/rack/level/bin 오름차순(동선 최적화)으로 조회한다. LIMIT은 호출부에서
     * {@code Pageable}로 지정한다(자동 추천은 1건만 필요).
     */
    @Query("""
            SELECT l FROM Location l
            WHERE l.warehouse.id = :warehouseId
              AND l.storageType = :storageType
              AND l.zone = :zone
              AND l.locationType = :locationType
              AND l.status = :status
              AND NOT EXISTS (
                  SELECT 1 FROM Inventory inv WHERE inv.location = l AND inv.quantity > 0
              )
              AND NOT EXISTS (
                  SELECT 1 FROM StockMovement sm WHERE sm.toLocation = l AND sm.status IN :activeMovementStatuses
              )
            ORDER BY l.aisle ASC, l.rack ASC, l.level ASC, l.bin ASC
            """)
    List<Location> findAvailableLocations(
            @Param("warehouseId") Long warehouseId,
            @Param("storageType") StorageType storageType,
            @Param("zone") Zone zone,
            @Param("locationType") LocationType locationType,
            @Param("status") LocationStatus status,
            @Param("activeMovementStatuses") Collection<MovementStatus> activeMovementStatuses,
            Pageable pageable);
}
