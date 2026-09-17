package com.kurly.wms.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record PutAwayConfirmRequest(
        @NotNull Long stockMovementId,
        @NotNull Long targetLocationId
) {
}
