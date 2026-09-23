package com.kurly.order.presentation.api;

import com.kurly.common.exception.GlobalErrorCode;
import com.kurly.common.response.ApiResponse;
import com.kurly.common.security.AuthenticatedPrincipal;
import com.kurly.common.swagger.ApiErrorCodeExample;
import com.kurly.order.domain.common.OrderErrorCode;
import com.kurly.order.infrastructure.dto.DeliveryAddressResponseDto;
import com.kurly.order.presentation.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "장바구니 API", description = "장바구니 조회 및 배송지 변경")
public interface CartApi {

    @Operation(summary = "장바구니 상품 추가", description = "상품을 장바구니에 추가합니다. 이미 담긴 상품이면 요청 수량만큼 합산합니다.")
    @ApiErrorCodeExample(status = GlobalErrorCode.class, code = "INVALID_INPUT_VALUE")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_INVALID_CART_ITEMS")
    ApiResponse<CartResponseDto> addItems(
            @Parameter(hidden = true) AuthenticatedPrincipal me,
            AddCartItemsRequestDto request
    );

    @Operation(summary = "장바구니 상세 조회", description = "로그인 사용자의 장바구니 품목과 선택된 배송지 정보를 조회합니다.")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_INCOMPLETE_PRODUCT_RESPONSE")
    ApiResponse<CartResponseDto> detail(@Parameter(hidden = true) AuthenticatedPrincipal me);

    @Operation(summary = "배송 약속 재조회 (배송지 변경)", description = "장바구니 배송지를 변경하고 배송 가능 약속 정보를 재계산합니다.")
    @ApiErrorCodeExample(status = GlobalErrorCode.class, code = "INVALID_INPUT_VALUE", message = "배송지 ID가 올바르지 않습니다.")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_NOT_FOUND_ADDRESS")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_NOT_FOUND_ADDRESS", message = "배송이 불가능한 지역입니다.")
    ApiResponse<DeliveryAddressResponseDto> edit(
            @Parameter(hidden = true) AuthenticatedPrincipal me,
            DeliveryAddressRequestDto request
    );

    @Operation(summary = "장바구니 상품 수량 변경", description = "장바구니에 담긴 특정 상품(productId)의 수량을 변경합니다.")
    @ApiErrorCodeExample(status = GlobalErrorCode.class, code = "INVALID_INPUT_VALUE", message = "수량은 최소 1개 이상이어야 합니다.")
    @ApiErrorCodeExample(status = GlobalErrorCode.class, code = "RESOURCE_NOT_FOUND", message = "장바구니 항목을 찾을 수 없습니다.")
    ApiResponse<Void> updateItemQuantity(
            @Parameter(hidden = true) AuthenticatedPrincipal me,
            @Parameter(description = "상품 ID (productId)", example = "301") Long productId,
            CartQuantityUpdateRequest request
    );

    @Operation(summary = "장바구니 단일 상품 삭제", description = "장바구니에서 특정 상품(productId) 1건을 삭제합니다.")
    @ApiErrorCodeExample(status = GlobalErrorCode.class, code = "RESOURCE_NOT_FOUND", message = "장바구니 항목을 찾을 수 없습니다.")
    ApiResponse<Void> deleteItem(
            @Parameter(hidden = true) AuthenticatedPrincipal me,
            @Parameter(description = "상품 ID (productId)", example = "301") Long productId
    );

    @Operation(summary = "장바구니 선택 상품 다중 삭제", description = "장바구니에서 선택된 여러 상품(productIds) 목록을 Body로 전달받아 삭제하고, 실제 삭제 처리된 상품 ID 목록을 반환합니다.")
    @ApiErrorCodeExample(status = GlobalErrorCode.class, code = "INVALID_INPUT_VALUE")
    ApiResponse<DeleteCartItemsResponseDto> deleteSelectedItems(
            @Parameter(hidden = true) AuthenticatedPrincipal me,
            DeleteCartItemsRequestDto request
    );
}
