package com.kurly.wms.presentation.dto;

import com.kurly.wms.infrastructure.entity.StockMovement.MovementType;
import com.kurly.wms.infrastructure.entity.StockMovement.MovementUnit;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StockMovementCreateRequest(
        @NotNull Long warehouseId,
        @NotNull Long inventoryId,
        @NotNull Long productId,
        @NotNull Long fromLocationId,
        @NotNull Long targetLocationId,

        @NotNull @Min(1)
        Integer unitQuantity,

        @NotNull MovementUnit movementUnit,
        @NotNull MovementType movementType
) {
}
