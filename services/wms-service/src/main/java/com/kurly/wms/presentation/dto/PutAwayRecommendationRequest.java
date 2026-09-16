package com.kurly.wms.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record PutAwayRecommendationRequest(
        @NotNull Long stockMovementId
) {
}
