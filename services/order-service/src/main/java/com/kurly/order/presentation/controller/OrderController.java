package com.kurly.order.presentation.controller;

import com.kurly.common.response.ApiResponse;
import com.kurly.common.security.AuthPrincipal;
import com.kurly.common.security.Authenticated;
import com.kurly.common.security.AuthenticatedPrincipal;
import com.kurly.order.application.OrderService;
import com.kurly.order.presentation.api.OrderApi;
import com.kurly.order.presentation.dto.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Authenticated
public class OrderController implements OrderApi {

    private final OrderService orderService;

    @Override
    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CheckoutResponseDto> checkout(
            @AuthPrincipal AuthenticatedPrincipal me,
            @Valid @RequestBody CheckoutRequestDto request
    ) {
        return ApiResponse.success("주문서 생성 성공", orderService.checkout(me, request));
    }

    @Override
    @GetMapping
    public ApiResponse<OrderPageResponseDto> list(
            @AuthPrincipal AuthenticatedPrincipal me,
            @RequestParam(defaultValue = "3M") String range,
            @RequestParam(required = false) String productName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success("주문 목록 조회에 성공했습니다.",
                orderService.getAll(me, range, productName, page, size));
    }

    @Override
    @GetMapping("/{orderId}")
    public ApiResponse<OrderDetailResponseDto> detail(
            @AuthPrincipal AuthenticatedPrincipal me,
            @PathVariable @Positive Long orderId
    ) {
        return ApiResponse.success("주문 상세 조회에 성공했습니다.", orderService.getById(me, orderId));
    }

    @Override
    @GetMapping("/cancellations-returns")
    public ApiResponse<ClaimHistoryPageResponseDto> listClaims(
            @AuthPrincipal AuthenticatedPrincipal me,
            @RequestParam(required = false) String requestType,
            @RequestParam(required = false) String requestStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success("취소·반품 내역을 조회했습니다.",
                orderService.getClaimHistories(me, requestType, requestStatus, page, size));
    }

    @Override
    @GetMapping("/{orderId}/returns/preview")
    public ApiResponse<ReturnPreviewResponseDto> returnPreview(
            @AuthPrincipal AuthenticatedPrincipal me,
            @PathVariable @Positive Long orderId
    ) {
        return ApiResponse.success("반품 접수 정보를 조회했습니다.",
                orderService.getReturnPreview(me, orderId));
    }

    @Override
    @PostMapping("/place-order")
    public ApiResponse<PlaceOrderResponseDto> placeOrder(
            @AuthPrincipal AuthenticatedPrincipal me,
            @Valid @RequestBody PlaceOrderRequestDto request
    ) {
        return ApiResponse.success("주문 결제 요청 성공", orderService.placeOrder(me, request.orderId()));
    }

    @Override
    @PostMapping("/{orderId}/cancel")
    public ApiResponse<OrderClaimResponseDto> cancel(
            @AuthPrincipal AuthenticatedPrincipal me,
            @PathVariable @Positive Long orderId,
            @Valid @RequestBody ClaimRequestDto request
    ) {
        return ApiResponse.success("전체 주문 취소가 접수되었습니다.", orderService.cancel(me, orderId, request));
    }

    @Override
    @PostMapping("/{orderId}/returns")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderClaimResponseDto> createReturn(
            @AuthPrincipal AuthenticatedPrincipal me,
            @PathVariable @Positive Long orderId,
            @Valid @RequestBody ReturnRequestDto request
    ) {
        return ApiResponse.success("전체 주문 반품이 접수되었습니다.",
                orderService.requestReturn(me, orderId, request));
    }
}