package com.kurly.oms.presentation.api;

import com.kurly.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "OMS 관리자 주문/반품 관제 API", description = "OMS 주문 모니터링, 직권 취소 및 반품 승인/역물류 처리")
public interface OmsAdminOrderApi {

    @Operation(summary = "OMS 주문 통합 관제 목록")
    ApiResponse<Object> listOrders();

    @Operation(summary = "OMS 주문 및 Shipment 상세 관제")
    ApiResponse<Object> getOrderMonitoring(Long omsOrderId);

    @Operation(summary = "CS 관리자 주문 직권 취소")
    ApiResponse<Void> cancelOrder(Long orderId);

    @Operation(summary = "반품 신청 내역 목록 조회 (Admin/BO)")
    ApiResponse<Object> listReturns();

    @Operation(summary = "반품 신청 상세 내역 조회 (Admin/BO)")
    ApiResponse<Object> getReturnDetail(Long returnId);

    @Operation(summary = "[냉동/냉장] 반품 승인 및 자체폐기 확정")
    ApiResponse<Void> approveColdChainReturn(Long returnId);

    @Operation(summary = "[상온/비식품] 반품 승인 및 역물류 지시")
    ApiResponse<Void> processLogisticsReturn(Long returnId);
}