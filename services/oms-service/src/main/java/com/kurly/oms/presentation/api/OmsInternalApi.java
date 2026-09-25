package com.kurly.oms.presentation.api;

import com.kurly.common.response.ApiResponse;
import com.kurly.oms.presentation.dto.CancelEligibilityResponseDto;
import com.kurly.oms.presentation.dto.DeliveryPromiseRequest;
import com.kurly.oms.presentation.dto.DeliveryPromiseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "OMS 내부 연동 API", description = "주문 취소 가능 여부 동기 확인, 배송 약속 조회 및 WMS 검수 결과 수신")
public interface OmsInternalApi {

    @Operation(summary = "배송 약속 조회")
    ApiResponse<DeliveryPromiseResponse> getDeliveryPromises(DeliveryPromiseRequest request);

    @Operation(summary = "주문 취소 가능 여부 확인")
    ApiResponse<CancelEligibilityResponseDto> checkCancelEligibility(Long orderId);

}