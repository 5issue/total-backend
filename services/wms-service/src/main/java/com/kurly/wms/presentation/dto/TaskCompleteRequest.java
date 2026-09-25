package com.kurly.wms.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record TaskCompleteRequest(
        @NotNull Long taskId,
        @NotNull Long workerId
) {
}
