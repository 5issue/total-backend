package com.kurly.wms.infrastructure.jpa;

import com.kurly.wms.infrastructure.entity.Inventory;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryJpaRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByWarehouseIdAndLocationIdAndProductIdAndLotNoAndExpiredDateAndLpnCode(
            Long warehouseId, Long locationId, Long productId, String lotNo, LocalDate expiredDate, String lpnCode);
}
