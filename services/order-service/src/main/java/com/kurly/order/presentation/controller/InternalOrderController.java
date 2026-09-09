package com.kurly.order.presentation.controller;

import com.kurly.common.response.ApiResponse;
import com.kurly.order.application.OrderService;
import com.kurly.order.presentation.dto.CompletePayRequestDto;
import com.kurly.order.presentation.dto.CompletePayResponseDto;
import com.kurly.order.presentation.dto.InternalOrderItemsResponseDto;
import com.kurly.order.presentation.dto.InternalOrderResponseDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/orders")
@RequiredArgsConstructor
public class InternalOrderController {

    private final OrderService orderService;

    @GetMapping("/{orderId}")
    public ApiResponse<InternalOrderResponseDto> detail(@PathVariable @Positive Long orderId) {
        return ApiResponse.success(orderService.getForPayment(orderId));
    }

    @GetMapping("/{orderId}/items")
    public ApiResponse<InternalOrderItemsResponseDto> listItems(@PathVariable @Positive Long orderId) {
        return ApiResponse.success(orderService.getItems(orderId));
    }

    @PostMapping("/{orderId}/complete-pay")
    public ApiResponse<CompletePayResponseDto> completePay(
            @PathVariable @Positive Long orderId,
            @Valid @RequestBody CompletePayRequestDto request
    ) {
        return ApiResponse.success("주문 결제 확정 성공", orderService.completePay(orderId, request));
    }
}
