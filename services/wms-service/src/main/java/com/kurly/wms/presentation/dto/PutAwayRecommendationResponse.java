package com.kurly.wms.presentation.dto;

import com.kurly.wms.domain.enums.StorageType;
import com.kurly.wms.infrastructure.entity.StockMovement.MovementStatus;

public record PutAwayRecommendationResponse(
        Long inboundItemId,
        Long stockMovementId,
        Long recommendedLocationId,
        StorageType storageType,
        String reason,
        MovementStatus status
) {
}
