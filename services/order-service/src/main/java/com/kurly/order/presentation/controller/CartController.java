package com.kurly.order.presentation.controller;

import com.kurly.common.response.ApiResponse;
import com.kurly.common.security.AuthPrincipal;
import com.kurly.common.security.Authenticated;
import com.kurly.common.security.AuthenticatedPrincipal;
import com.kurly.order.application.CartService;
import com.kurly.order.infrastructure.dto.DeliveryAddressResponseDto;
import com.kurly.order.presentation.api.CartApi;
import com.kurly.order.presentation.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/carts")
@RequiredArgsConstructor
@Authenticated
public class CartController implements CartApi {

    private final CartService cartService;

    @Override
    @PostMapping("/items")
    public ApiResponse<CartResponseDto> addItems(
            @AuthPrincipal AuthenticatedPrincipal me,
            @Valid @RequestBody AddCartItemsRequestDto request
    ) {
        return ApiResponse.success("장바구니에 상품을 추가했습니다.",
                cartService.addItems(me, request));
    }

    @Override
    @GetMapping
    public ApiResponse<CartResponseDto> detail(@AuthPrincipal AuthenticatedPrincipal me) {
        return ApiResponse.success("장바구니 조회에 성공했습니다.",
                cartService.getByMemberId(me));
    }

    @Override
    @PutMapping("/delivery-address")
    public ApiResponse<DeliveryAddressResponseDto> edit(
            @AuthPrincipal AuthenticatedPrincipal me,
            @Valid @RequestBody DeliveryAddressRequestDto request
    ) {
        return ApiResponse.success("배송 약속 재조회에 성공했습니다.",
                cartService.updateDeliveryAddress(me, request.addressId()));
    }

    @PatchMapping("/items/{productId}")
    public ApiResponse<Void> updateItemQuantity(
            @AuthPrincipal AuthenticatedPrincipal me,
            @PathVariable Long productId,
            @Valid @RequestBody CartQuantityUpdateRequest request
    ) {
        cartService.updateItemQuantity(me, productId, request.quantity());
        return ApiResponse.success(null);
    }

    @DeleteMapping("/items/{productId}")
    public ApiResponse<Void> deleteItem(
            @AuthPrincipal AuthenticatedPrincipal me,
            @PathVariable Long productId
    ) {
        cartService.deleteItem(me, productId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/items")
    public ApiResponse<DeleteCartItemsResponseDto> deleteSelectedItems(
            @AuthPrincipal AuthenticatedPrincipal me,
            @Valid @RequestBody DeleteCartItemsRequestDto request
    ) {
        List<Long> deletedProductIds = cartService.deleteSelectedItems(me, request.productIds());
        return ApiResponse.success(DeleteCartItemsResponseDto.from(deletedProductIds));
    }
}
