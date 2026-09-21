package com.kurly.oms.presentation.api;

import com.kurly.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "OMS 관리자 풀필먼트/CAPA 관제 API", description = "센터 회차 CAPA, TAM 권역, 배송 슬롯 및 Shipment 이월 제어")
public interface OmsAdminFulfillmentApi {

    @Operation(summary = "센터 회차 CAPA 수동 조정")
    ApiResponse<Void> adjustCapacity(Long capacityPlanId);

    @Operation(summary = "센터 회차 CAPA 목록 조회")
    ApiResponse<Object> listCapacities();

    @Operation(summary = "Shipment 긴급 자동 이월 실행")
    ApiResponse<Void> rolloverShipment(Long shipmentId);

    @Operation(summary = "권역별 배송 회차 및 Cutoff 변경")
    ApiResponse<Void> editDeliverySlotCutoff(Long slotId);

    @Operation(summary = "TAM 배송 권역 목록 조회")
    ApiResponse<Object> listRegions();

    @Operation(summary = "TAM 배송 권역 운영 상태 변경")
    ApiResponse<Void> editRegionStatus(Long regionId);

    @Operation(summary = "권역별 배송 회차 목록 조회")
    ApiResponse<Object> listDeliverySlots();
}