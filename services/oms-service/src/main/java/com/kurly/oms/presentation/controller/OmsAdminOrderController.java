package com.kurly.oms.presentation.controller;

import com.kurly.common.response.ApiResponse;
import com.kurly.common.security.RequireRole;
import com.kurly.common.security.Role;
import com.kurly.oms.application.OmsOrderService;
import com.kurly.oms.application.OmsReturnService;
import com.kurly.oms.presentation.api.OmsAdminOrderApi;
import com.kurly.oms.presentation.dto.OmsOrderDetailResponse;
import com.kurly.oms.presentation.dto.OmsOrderListResponse;
import com.kurly.oms.presentation.dto.OmsOrderSearchCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/admin/oms")
@RequireRole(Role.ADMIN)
@RequiredArgsConstructor
public class OmsAdminOrderController implements OmsAdminOrderApi {

    private final OmsOrderService orderService;
    private final OmsReturnService returnService;

    @Override
    @GetMapping("/orders")
    public ApiResponse<OmsOrderListResponse> listOrders(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long centerId,
            @RequestParam(required = false) LocalDateTime startAt,
            @RequestParam(required = false) LocalDateTime endAt,
            Pageable pageable
    ) {
        return ApiResponse.success(orderService.listOrders(
                new OmsOrderSearchCondition(orderNo, status, regionId, centerId, startAt, endAt),
                pageable
        ));
    }

    @Override
    @GetMapping("/orders/{omsOrderId}")
    public ApiResponse<OmsOrderDetailResponse> getOrderDetail(@PathVariable Long omsOrderId) {
        return ApiResponse.success(orderService.getOrderDetail(omsOrderId));
    }

    @Override
    @PostMapping("/orders/{orderId}/cancel")
    public ApiResponse<Void> cancelOrder(@PathVariable Long orderId) {
        orderService.cancelOrderByAdmin(orderId);
        return ApiResponse.success();
    }

//    @Operation(summary = "반품 신청 내역 목록 조회 (Admin/BO)")
//    @Override
//    @GetMapping("/returns")
//    public ApiResponse<ReturnProcessListDto> listReturns(
//            @RequestParam(required = false) OmsReturnStatus status,
//            @RequestParam(required = false) StorageType storageType,
//            @RequestParam(defaultValue = "1") int page,
//            @RequestParam(defaultValue = "20") int size) {
//        return ApiResponse.success(returnService.listReturns(status, storageType, page, size));
//    }

    //    @Override
//    @GetMapping("/returns/{returnId}")
//    public ApiResponse<ReturnProcessDto> getReturnDetail(@PathVariable Long returnId) {
//        return ApiResponse.success(returnService.getReturnDetail(returnId));
//    }
//
    @Override
    @PostMapping("/returns/{returnId}/approve-coldchain")
    public ApiResponse<Void> approveColdChainReturn(@PathVariable Long returnId) {
        returnService.approveColdChainReturn(returnId);
        return ApiResponse.success();
    }

    @Override
    @PostMapping("/returns/{returnId}/process-logistics")
    public ApiResponse<Void> processLogisticsReturn(@PathVariable Long returnId) {
        returnService.processLogisticsReturn(returnId);
        return ApiResponse.success();
    }
}
