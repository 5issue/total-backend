package com.kurly.order.presentation.api;

import com.kurly.common.response.ApiResponse;
import com.kurly.common.swagger.ApiErrorCodeExample;
import com.kurly.order.domain.common.OrderErrorCode;
import com.kurly.order.presentation.dto.CompletePayRequestDto;
import com.kurly.order.presentation.dto.CompletePayResponseDto;
import com.kurly.order.presentation.dto.InternalOrderItemsResponseDto;
import com.kurly.order.presentation.dto.InternalOrderResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "내부 주문 API", description = "결제/상품 서비스 연동 전용 API")
public interface InternalOrderApi {

    @Operation(summary = "결제 검증용 주문 조회", description = "결제 서비스에서 주문 금액 및 재고 예약 만료 시간을 조회합니다.")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_NOT_FOUND_ORDER")
    ApiResponse<InternalOrderResponseDto> detail(@Parameter(description = "주문 ID") Long orderId);

    @Operation(summary = "재고 확정용 주문 품목 조회", description = "결제 완료 후 상품 서비스에서 재고 확정 처리를 위한 품목 목록을 조회합니다.")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_NOT_FOUND_ORDER")
    ApiResponse<InternalOrderItemsResponseDto> listItems(@Parameter(description = "주문 ID") Long orderId);

    @Operation(summary = "결제 완료 동기 확정 통보", description = "PG 결제 성공 직후 주문 서비스로 주문을 PAID 상태로 전이합니다.")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_NOT_FOUND_ORDER")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_CONFLICT_ALREADY_PAID")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_INVALID_STATUS", message = "주문 금액과 결제 금액이 일치하지 않습니다.")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_EXPIRED_PAYMENT_TIMEOUT")
    ApiResponse<CompletePayResponseDto> completePay(
            @Parameter(description = "주문 ID") Long orderId,
            CompletePayRequestDto request
    );
}