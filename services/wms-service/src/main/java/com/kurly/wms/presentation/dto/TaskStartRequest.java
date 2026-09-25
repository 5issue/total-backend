package com.kurly.wms.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record TaskStartRequest(
        @NotNull Long taskId,
        @NotNull Long workerId
) {
}
