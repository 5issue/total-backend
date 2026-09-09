package com.kurly.order.presentation.controller;

import com.kurly.common.response.ApiResponse;
import com.kurly.common.exception.UnauthorizedException;
import com.kurly.order.application.OrderService;
import com.kurly.order.presentation.dto.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CheckoutResponseDto> checkout(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CheckoutRequestDto request
    ) {
        return ApiResponse.success("주문서 생성 성공", orderService.checkout(memberId(jwt), request));
    }

    @GetMapping
    public ApiResponse<OrderPageResponseDto> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "3M") String range,
            @RequestParam(required = false) String productName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success("주문 목록 조회에 성공했습니다.",
                orderService.getAll(memberId(jwt), range, productName, page, size));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderDetailResponseDto> detail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive Long orderId
    ) {
        return ApiResponse.success("주문 상세 조회에 성공했습니다.", orderService.getById(memberId(jwt), orderId));
    }

    @GetMapping("/cancellations-returns")
    public ApiResponse<ClaimHistoryPageResponseDto> listClaims(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String requestType,
            @RequestParam(required = false) String requestStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success("취소·반품 내역을 조회했습니다.",
                orderService.getClaimHistories(memberId(jwt), requestType, requestStatus, page, size));
    }

    @GetMapping("/{orderId}/returns/preview")
    public ApiResponse<ReturnPreviewResponseDto> returnPreview(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive Long orderId
    ) {
        return ApiResponse.success("반품 접수 정보를 조회했습니다.",
                orderService.getReturnPreview(memberId(jwt), orderId));
    }

    @PostMapping("/place-order")
    public ApiResponse<PlaceOrderResponseDto> placeOrder(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PlaceOrderRequestDto request
    ) {
        return ApiResponse.success("주문 결제 요청 성공", orderService.placeOrder(memberId(jwt), request.orderId()));
    }

    @PostMapping("/{orderId}/cancel")
    public ApiResponse<OrderClaimResponseDto> cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive Long orderId,
            @Valid @RequestBody ClaimRequestDto request
    ) {
        return ApiResponse.success("전체 주문 취소가 접수되었습니다.", orderService.cancel(memberId(jwt), orderId, request));
    }

    @PostMapping("/{orderId}/returns")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderClaimResponseDto> createReturn(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive Long orderId,
            @Valid @RequestBody ReturnRequestDto request
    ) {
        return ApiResponse.success("전체 주문 반품이 접수되었습니다.",
                orderService.requestReturn(memberId(jwt), orderId, request));
    }

    private Long memberId(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (RuntimeException exception) {
            throw new UnauthorizedException("인증 토큰의 사용자 식별자가 올바르지 않습니다.");
        }
    }
}
