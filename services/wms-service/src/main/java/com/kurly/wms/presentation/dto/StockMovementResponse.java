package com.kurly.wms.presentation.dto;

import com.kurly.wms.infrastructure.entity.StockMovement;
import com.kurly.wms.infrastructure.entity.StockMovement.MovementStatus;
import com.kurly.wms.infrastructure.entity.StockMovement.MovementType;
import com.kurly.wms.infrastructure.entity.StockMovement.MovementUnit;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record StockMovementResponse(
        Long id,
        Long warehouseId,
        Long productId,
        Long inboundItemId,
        String lotNo,
        LocalDate expiredDate,
        Long fromLocationId,
        Long toLocationId,
        MovementUnit movementUnit,
        Integer unitQuantity,
        Integer quantity,
        MovementType movementType,
        MovementStatus status,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
    public static StockMovementResponse from(StockMovement movement) {
        return new StockMovementResponse(
                movement.getId(),
                movement.getWarehouse().getId(),
                movement.getProduct().getId(),
                movement.getInboundItem() != null ? movement.getInboundItem().getId() : null,
                movement.getLotNo(),
                movement.getExpiredDate(),
                movement.getFromLocation() != null ? movement.getFromLocation().getId() : null,
                movement.getToLocation() != null ? movement.getToLocation().getId() : null,
                movement.getMovementUnit(),
                movement.getUnitQuantity(),
                movement.getQuantity(),
                movement.getMovementType(),
                movement.getStatus(),
                movement.getCreatedAt(),
                movement.getCompletedAt()
        );
    }
}
