package com.kurly.wms.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

public record InboundAsnCreateRequest(
        @NotNull Long warehouseId,
        @NotBlank String poNumber,
        @NotBlank String supplierName,
        @NotNull LocalDateTime expectedDate,
        @NotEmpty List<@Valid InboundAsnItemRequest> items
) {
}
