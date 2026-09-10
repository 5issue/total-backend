package com.kurly.order.presentation.controller;

import com.kurly.common.response.ApiResponse;
import com.kurly.common.security.AuthPrincipal;
import com.kurly.common.security.Authenticated;
import com.kurly.common.security.AuthenticatedPrincipal;
import com.kurly.order.application.CartService;
import com.kurly.order.presentation.api.CartApi;
import com.kurly.order.presentation.dto.CartResponseDto;
import com.kurly.order.presentation.dto.DeliveryAddressRequestDto;
import com.kurly.order.presentation.dto.DeliveryAddressResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/carts")
@RequiredArgsConstructor
@Authenticated
public class CartController implements CartApi {

    private final CartService cartService;

    @Override
    @GetMapping
    public ApiResponse<CartResponseDto> detail(@AuthPrincipal AuthenticatedPrincipal principal) {
        return ApiResponse.success("장바구니 조회에 성공했습니다.",
                cartService.getByMemberId(principal.userId()));
    }

    @Override
    @PutMapping("/delivery-address")
    public ApiResponse<DeliveryAddressResponseDto> edit(
            @AuthPrincipal AuthenticatedPrincipal principal,
            @Valid @RequestBody DeliveryAddressRequestDto request
    ) {
        return ApiResponse.success("배송 약속 재조회에 성공했습니다.",
                cartService.updateDeliveryAddress(principal.userId(), request.addressId()));
    }
}