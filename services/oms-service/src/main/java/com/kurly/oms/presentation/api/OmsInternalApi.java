package com.kurly.oms.presentation.api;

import com.kurly.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "OMS 내부 연동 API", description = "주문 취소 가능 여부 동기 확인, 배송 약속 조회 및 WMS 검수 결과 수신")
public interface OmsInternalApi {

    @Operation(summary = "배송 약속 조회")
    ApiResponse<Object> getDeliveryPromises();

    @Operation(summary = "주문 취소 가능 여부 확인")
    ApiResponse<Boolean> checkCancelEligibility(Long orderId);

    @Operation(summary = "WMS 반품 입고/검수 결과 수신")
    ApiResponse<Void> receiveInspectionResult(Long returnId);
}