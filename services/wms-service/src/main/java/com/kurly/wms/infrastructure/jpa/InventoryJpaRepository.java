package com.kurly.wms.infrastructure.jpa;

import com.kurly.wms.domain.enums.StorageType;
import com.kurly.wms.infrastructure.entity.Inventory;
import com.kurly.wms.infrastructure.entity.Location.Zone;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryJpaRepository extends JpaRepository<Inventory, Long> {
    /**
     * receiveIntoBuffer/moveInventory가 "조회 후 quantity 증감"에 쓰는 조회라 PESSIMISTIC_WRITE로
     * 잠근다 — 같은 (warehouse, location, product, lot, expiredDate, lpnCode) 행을 동시에 건드리는
     * 다른 트랜잭션은 이 트랜잭션이 끝날 때까지 대기하므로, 낙관적 락과 달리 lost update 자체가
     * 발생하지 않는다(증가만 하는 두 트랜잭션이 서로 실패시키지도 않는다).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Inventory> findByWarehouseIdAndLocationIdAndProductIdAndLotNoAndExpiredDateAndLpnCode(
            Long warehouseId, Long locationId, Long productId, String lotNo, LocalDate expiredDate, String lpnCode);

    /**
     * StockMovementQueryService.create()가 "가용 수량 확인 → reserve()"를 원자적으로 하기 위해
     * 쓰는 조회. 잠그지 않으면 같은 inventoryId를 대상으로 한 동시 요청이 둘 다 가용 수량 검증을
     * 통과해 실제 재고보다 많이 예약(over-reserve)할 수 있다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Inventory> findWithPessimisticLockById(Long id);

    /**
     * 상품별 재고 요약(GET /api/v1/wms/inventories/summary)용 집계 조회. warehouseId/productId는
     * 둘 다 선택 조건이다 — warehouseId가 null이면 전국 전체 창고를 상품 단위로 통합 집계한다.
     * 응답의 warehouseId(요청값을 그대로 되돌려주는 것뿐)는 서비스 계층에서 채운다.
     */
    @Query(value = """
            SELECT new com.kurly.wms.infrastructure.jpa.ProductInventorySummaryProjection(
                i.product.id, SUM(i.quantity), SUM(i.reservedQuantity)
            )
            FROM Inventory i
            WHERE (:warehouseId IS NULL OR i.warehouse.id = :warehouseId)
              AND (:productId IS NULL OR i.product.id = :productId)
            GROUP BY i.product.id
            ORDER BY i.product.id ASC
            """,
            countQuery = """
            SELECT COUNT(DISTINCT i.product.id)
            FROM Inventory i
            WHERE (:warehouseId IS NULL OR i.warehouse.id = :warehouseId)
              AND (:productId IS NULL OR i.product.id = :productId)
            """)
    Page<ProductInventorySummaryProjection> summarize(
            @Param("warehouseId") Long warehouseId,
            @Param("productId") Long productId,
            Pageable pageable);


    @Query(value = """
            SELECT i FROM Inventory i
            JOIN FETCH i.location l
            JOIN FETCH i.warehouse
            JOIN FETCH i.product
            WHERE (:warehouseId IS NULL OR i.warehouse.id = :warehouseId)
              AND (:productId IS NULL OR i.product.id = :productId)
              AND (:zone IS NULL OR l.zone = :zone)
              AND (:locationId IS NULL OR l.id = :locationId)
              AND (:lotNo IS NULL OR i.lotNo = :lotNo)
            ORDER BY i.id ASC
            """,
            countQuery = """
            SELECT COUNT(i) FROM Inventory i
            JOIN i.location l
            WHERE (:warehouseId IS NULL OR i.warehouse.id = :warehouseId)
              AND (:productId IS NULL OR i.product.id = :productId)
              AND (:zone IS NULL OR l.zone = :zone)
              AND (:locationId IS NULL OR l.id = :locationId)
              AND (:lotNo IS NULL OR i.lotNo = :lotNo)
            """)
    Page<Inventory> search(
            @Param("warehouseId") Long warehouseId,
            @Param("productId") Long productId,
            @Param("zone") Zone zone,
            @Param("locationId") Long locationId,
            @Param("lotNo") String lotNo,
            Pageable pageable);

    boolean existsByLocationIdAndQuantityGreaterThan(Long id, int i);

    /**
     * 출고 FEFO 하드 할당(피킹존) 및 보충 지시 소싱(보관존) 양쪽에서 쓰는 후보 조회. 가용
     * 수량(quantity-reservedQuantity)이 있는 행만, 유통기한 오름차순(FEFO)으로 반환한다.
     * 같은 상품을 동시에 할당하는 여러 트랜잭션이 가용 수량을 중복으로 보고 초과 예약
     * (over-reserve)하지 않도록 PESSIMISTIC_WRITE로 잠근다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT i FROM Inventory i
            JOIN i.location l
            WHERE i.warehouse.id = :warehouseId
              AND i.product.id = :productId
              AND l.zone = :zone
              AND (i.quantity - i.reservedQuantity) > 0
            ORDER BY i.expiredDate ASC NULLS LAST, i.id ASC
            """)
    List<Inventory> findAllocatableByFefo(
            @Param("warehouseId") Long warehouseId,
            @Param("productId") Long productId,
            @Param("zone") Zone zone);

    /**
     * 보충 지시(REPLENISHMENT)의 목적지를 정하기 위해, 이 상품이 이미 피킹존 어느 로케이션에
     * 배치돼 있는지 찾는다. 피킹존 로케이션 배정 전략(고정 슬롯 vs 동적 배정)이 아직 erd-spec의
     * 미결 사항이라, "이미 재고가 있던 로케이션을 그대로 재사용"하는 것 이상은 하지 않는다 —
     * 이 상품이 피킹존에 한 번도 배치된 적 없으면 빈 값을 반환하고, 호출부가 보충을 보류한다.
     * 로케이션의 storage_type이 상품의 storage_type과 일치하는 것만 대상으로 한다 — 그렇지
     * 않으면 여기서 고른 로케이션으로 REPLENISHMENT 이동 지시를 만들어도, 나중에 실제 물리 이동
     * 확정(StockMovementQueryService.confirm) 시점에야 validateTargetLocation()이 보관 유형
     * 불일치로 막아서 그 이동 지시가 영영 확정 불가능한 상태로 남는다.
     */
    Optional<Inventory> findFirstByWarehouseIdAndProductIdAndLocation_ZoneAndLocation_StorageType(
            Long warehouseId, Long productId, Zone zone, StorageType storageType);
}
