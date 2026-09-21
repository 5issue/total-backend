package com.kurly.wms.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record StockMovementConfirmRequest(
        @NotNull Long stockMovementId,
        @NotNull Long targetLocationId
) {
}
