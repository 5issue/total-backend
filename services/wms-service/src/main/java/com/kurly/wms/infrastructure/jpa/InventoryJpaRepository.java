package com.kurly.wms.infrastructure.jpa;

import com.kurly.wms.infrastructure.entity.Inventory;
import com.kurly.wms.infrastructure.entity.Location.Zone;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
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
}
