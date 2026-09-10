package com.kurly.order.presentation.controller;

import com.kurly.common.response.ApiResponse;
import com.kurly.common.security.PublicApi;
import com.kurly.order.application.OrderService;
import com.kurly.order.presentation.api.InternalOrderApi;
import com.kurly.order.presentation.dto.CompletePayRequestDto;
import com.kurly.order.presentation.dto.CompletePayResponseDto;
import com.kurly.order.presentation.dto.InternalOrderItemsResponseDto;
import com.kurly.order.presentation.dto.InternalOrderResponseDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/orders")
@RequiredArgsConstructor
@PublicApi
public class InternalOrderController implements InternalOrderApi {

    private final OrderService orderService;

    @Override
    @GetMapping("/{orderId}")
    public ApiResponse<InternalOrderResponseDto> detail(@PathVariable @Positive Long orderId) {
        return ApiResponse.success(orderService.getForPayment(orderId));
    }

    @Override
    @GetMapping("/{orderId}/items")
    public ApiResponse<InternalOrderItemsResponseDto> listItems(@PathVariable @Positive Long orderId) {
        return ApiResponse.success(orderService.getItems(orderId));
    }

    @Override
    @PostMapping("/{orderId}/complete-pay")
    public ApiResponse<CompletePayResponseDto> completePay(
            @PathVariable @Positive Long orderId,
            @Valid @RequestBody CompletePayRequestDto request
    ) {
        return ApiResponse.success("주문 결제 확정 성공", orderService.completePay(orderId, request));
    }
}