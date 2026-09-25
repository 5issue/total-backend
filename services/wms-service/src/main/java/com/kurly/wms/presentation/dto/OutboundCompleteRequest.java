package com.kurly.wms.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record OutboundCompleteRequest(
        @NotNull Long outboundOrderId
) {
}
