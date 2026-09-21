package com.kurly.oms.presentation.dto;

public record InspectionResultRequest(
        String inspectionResult, // PASSED, FAILED
        boolean restockable,     // 재판매 가능 여부
        String faultType,        // CUSTOMER, SELLER
        String wmsNote
) {
}
