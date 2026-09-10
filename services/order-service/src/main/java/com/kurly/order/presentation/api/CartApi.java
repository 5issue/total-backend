package com.kurly.order.presentation.api;

import com.kurly.common.exception.GlobalErrorCode;
import com.kurly.common.response.ApiResponse;
import com.kurly.common.security.AuthenticatedPrincipal;
import com.kurly.common.swagger.ApiErrorCodeExample;
import com.kurly.order.domain.common.OrderErrorCode;
import com.kurly.order.presentation.dto.CartResponseDto;
import com.kurly.order.presentation.dto.DeliveryAddressRequestDto;
import com.kurly.order.presentation.dto.DeliveryAddressResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "장바구니 API", description = "장바구니 조회 및 배송지 변경")
public interface CartApi {

    @Operation(summary = "장바구니 상세 조회", description = "로그인 사용자의 장바구니 품목과 선택된 배송지 정보를 조회합니다.")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_INCOMPLETE_PRODUCT_RESPONSE")
    ApiResponse<CartResponseDto> detail(@Parameter(hidden = true) AuthenticatedPrincipal principal);

    @Operation(summary = "배송 약속 재조회 (배송지 변경)", description = "장바구니 배송지를 변경하고 배송 가능 약속 정보를 재계산합니다.")
    @ApiErrorCodeExample(status = GlobalErrorCode.class, code = "INVALID_INPUT_VALUE", message = "배송지 ID가 올바르지 않습니다.")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_NOT_FOUND_ADDRESS")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_NOT_FOUND_ADDRESS", message = "배송이 불가능한 지역입니다.")
    ApiResponse<DeliveryAddressResponseDto> edit(
            @Parameter(hidden = true) AuthenticatedPrincipal principal,
            DeliveryAddressRequestDto request
    );
}