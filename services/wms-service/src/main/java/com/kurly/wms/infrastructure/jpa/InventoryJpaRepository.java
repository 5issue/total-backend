package com.kurly.wms.infrastructure.jpa;

import com.kurly.wms.infrastructure.entity.Inventory;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

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
}
