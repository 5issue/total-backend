package com.kurly.wms.presentation.dto;

import com.kurly.wms.infrastructure.entity.InboundItem.InboundUnit;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record InboundAsnItemRequest(
        @NotNull Long productId,
        @NotNull InboundUnit inboundUnit,
        @NotNull @Min(1) Integer orderedQuantity
) {
}
