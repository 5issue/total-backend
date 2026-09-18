package com.kurly.oms.presentation.controller;

import com.kurly.common.response.ApiResponse;
import com.kurly.common.security.RequireRole;
import com.kurly.common.security.Role;
import com.kurly.oms.application.OmsOrderService;
import com.kurly.oms.application.OmsReturnService;
import com.kurly.oms.presentation.api.OmsAdminOrderApi;
import com.kurly.oms.presentation.dto.*;
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

    @PostMapping("/orders/{omsOrderId}/return-judgement")
    public ApiResponse<ReturnJudgementResponse> judgeReturn(
            @PathVariable Long omsOrderId,
            @RequestBody ReturnJudgementRequest request
    ) {
        return ApiResponse.success(
                "반품 판정이 완료되었습니다.",
                returnService.judgeReturn(omsOrderId, request)
        );
    }
}
