package com.kurly.order.presentation.controller;

import com.kurly.common.exception.UnauthorizedException;
import com.kurly.common.response.ApiResponse;
import com.kurly.order.application.CartService;
import com.kurly.order.presentation.dto.CartResponseDto;
import com.kurly.order.presentation.dto.DeliveryAddressRequestDto;
import com.kurly.order.presentation.dto.DeliveryAddressResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/carts")
@RequiredArgsConstructor
public class CartController {
    private final CartService cartService;

    @GetMapping
    public ApiResponse<CartResponseDto> detail(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success("장바구니 조회에 성공했습니다.", cartService.getByMemberId(memberId(jwt)));
    }

    @PutMapping("/delivery-address")
    public ApiResponse<DeliveryAddressResponseDto> edit(@AuthenticationPrincipal Jwt jwt,
                                                        @Valid @RequestBody DeliveryAddressRequestDto request) {
        return ApiResponse.success("배송 약속 재조회에 성공했습니다.",
                cartService.updateDeliveryAddress(memberId(jwt), request.addressId()));
    }

    private Long memberId(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (RuntimeException exception) {
            throw new UnauthorizedException("인증 토큰의 사용자 식별자가 올바르지 않습니다.");
        }
    }
}
