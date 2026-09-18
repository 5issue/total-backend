package com.kurly.order.presentation.dto;

import java.time.LocalDateTime;
import java.util.List;


public record ReturnListResponse(
        long total,
        int page,
        int size,
        List<ReturnSummary> items
) {
    public record ReturnSummary(
            Long returnId,
            Long orderId,
            String orderNo,
            Long memberId,
            String storageType,
            String reasonCode,
            String status,
            LocalDateTime requestedAt
    ) {
    }
}