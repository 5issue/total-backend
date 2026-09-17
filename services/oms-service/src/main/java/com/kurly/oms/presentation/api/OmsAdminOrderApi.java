package com.kurly.oms.presentation.api;

import com.kurly.common.response.ApiResponse;
import com.kurly.oms.presentation.dto.OmsOrderDetailResponse;
import com.kurly.oms.presentation.dto.OmsOrderListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

@Tag(name = "OMS 관리자 주문/반품 관제 API", description = "OMS 주문 모니터링, 직권 취소 및 반품 승인/역물류 처리")
public interface OmsAdminOrderApi {


    @Operation(summary = "OMS 주문 목록 조회", description = "주문번호, 기간, OMS 상태, 권역, 센터로 OMS 주문을 검색합니다.")
    ApiResponse<OmsOrderListResponse> listOrders(
            @Parameter(description = "주문번호") String orderNo,
            @Parameter(description = "OMS 주문 상태") String status,
            @Parameter(description = "권역 ID") Long regionId,
            @Parameter(description = "센터 ID") Long centerId,
            @Parameter(description = "검색 시작일시") LocalDateTime startAt,
            @Parameter(description = "검색 종료일시") LocalDateTime endAt,
            @Parameter(hidden = true) Pageable pageable
    );

    @Operation(summary = "OMS 주문 상세 조회", description = "OMS 주문, 온도대별 Shipment, 센터·회차 정보를 상세 조회합니다.")
    ApiResponse<OmsOrderDetailResponse> getOrderDetail(
            @Parameter(description = "OMS 주문 ID") Long omsOrderId
    );

    @Operation(summary = "CS 관리자 주문 직권 취소")
    ApiResponse<Void> cancelOrder(Long orderId);

//    @Operation(summary = "반품 신청 내역 목록 조회 (Admin/BO)")
//    ApiResponse<ReturnProcessListDto> listReturns(OmsReturnStatus status, StorageType storageType, int page, int size);

//    @Operation(summary = "반품 신청 상세 내역 조회 (Admin/BO)")
//    ApiResponse<ReturnProcessDto> getReturnDetail(Long returnId);

    @Operation(summary = "[냉동/냉장] 반품 승인 및 자체폐기 확정")
    ApiResponse<Void> approveColdChainReturn(Long returnId);

    @Operation(summary = "[상온/비식품] 반품 승인 및 역물류 지시")
    ApiResponse<Void> processLogisticsReturn(Long returnId);
}
