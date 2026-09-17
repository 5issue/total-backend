package com.kurly.wms.presentation.dto;

import com.kurly.wms.infrastructure.entity.InboundItem.InboundUnit;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record InspectItemRequest(
        @NotNull Long inboundItemId,
        @NotNull @Min(0) Integer inspectQuantity,
        InboundUnit inboundUnit,
        @NotBlank String lotNo,
        @NotNull LocalDate expiredDate,
        Long targetLocationId
) {
}
