package com.kurly.wms.presentation.dto;

import com.kurly.wms.infrastructure.entity.Inventory;
import com.kurly.wms.infrastructure.entity.Location;
import com.kurly.wms.infrastructure.entity.Location.Zone;
import java.time.LocalDate;

public record InventoryDetailResponse(
        Long inventoryId,
        Long warehouseId,
        Long locationId,

        /** Location에 아직 코드 컬럼이 없어 aisle-rack-level-bin을 조합해 임시로 만든 표시용 값. */
        String locationCode,

        Zone zone,
        Long productId,
        String lotNo,
        LocalDate expiredDate,
        String lpnCode,
        Integer quantity,
        Integer reservedQuantity,
        Integer availableQuantity
) {
    public static InventoryDetailResponse from(Inventory inventory) {
        Location location = inventory.getLocation();
        return new InventoryDetailResponse(
                inventory.getId(),
                inventory.getWarehouse().getId(),
                location.getId(),
                formatLocationCode(location),
                location.getZone(),
                inventory.getProduct().getId(),
                inventory.getLotNo(),
                inventory.getExpiredDate(),
                inventory.getLpnCode(),
                inventory.getQuantity(),
                inventory.getReservedQuantity(),
                inventory.getAvailableQuantity()
        );
    }

    private static String formatLocationCode(Location location) {
        return "%s-%s-%d-%s".formatted(location.getAisle(), location.getRack(), location.getLevel(), location.getBin());
    }
}
