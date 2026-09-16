package com.kurly.oms.presentation.controller;

import com.kurly.common.response.ApiResponse;
import com.kurly.common.security.RequireRole;
import com.kurly.common.security.Role;
import com.kurly.oms.application.OmsOrderService;
import com.kurly.oms.application.OmsReturnService;
import com.kurly.oms.presentation.api.OmsAdminOrderApi;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/oms")
@RequireRole(Role.ADMIN)
@RequiredArgsConstructor
public class OmsAdminOrderController implements OmsAdminOrderApi {

    private final OmsOrderService orderService;
    private final OmsReturnService returnService;

    @Override
    @GetMapping("/orders")
    public ApiResponse<Object> listOrders() {
        return ApiResponse.success(orderService.listOrders());
    }

    @Override
    @GetMapping("/orders/{omsOrderId}")
    public ApiResponse<Object> getOrderMonitoring(@PathVariable Long omsOrderId) {
        return ApiResponse.success(orderService.getOrderMonitoring(omsOrderId));
    }

    @Override
    @PostMapping("/orders/{orderId}/cancel")
    public ApiResponse<Void> cancelOrder(@PathVariable Long orderId) {
        orderService.cancelOrderByAdmin(orderId);
        return ApiResponse.success();
    }

    @Override
    @GetMapping("/returns")
    public ApiResponse<Object> listReturns() {
        return ApiResponse.success(returnService.listReturns());
    }

    @Override
    @GetMapping("/returns/{returnId}")
    public ApiResponse<Object> getReturnDetail(@PathVariable Long returnId) {
        return ApiResponse.success(returnService.getReturnDetail(returnId));
    }

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