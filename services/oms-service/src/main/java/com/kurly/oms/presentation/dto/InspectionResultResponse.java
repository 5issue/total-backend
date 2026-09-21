package com.kurly.oms.presentation.dto;

public record InspectionResultResponse(
        Long returnId,
        String returnStatus,
        String inspectionResult,
        Long finalRefundAmount,
        Long deductedFee
) {
}