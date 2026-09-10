package com.kurly.order.presentation.api;

import com.kurly.common.exception.GlobalErrorCode;
import com.kurly.common.response.ApiResponse;
import com.kurly.common.security.AuthenticatedPrincipal;
import com.kurly.common.swagger.ApiErrorCodeExample;
import com.kurly.order.domain.common.OrderErrorCode;
import com.kurly.order.presentation.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "주문 API", description = "주문서 생성, 결제 요청, 취소 및 반품 관리")
public interface OrderApi {

    @Operation(summary = "주문서 생성 (체크아웃)", description = "선택한 장바구니 상품으로 주문서를 생성하고 재고를 선점합니다.")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_INVALID_CART_ITEMS")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_NOT_FOUND_ADDRESS")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_INSUFFICIENT_STOCK")
    ApiResponse<CheckoutResponseDto> checkout(
            @Parameter(hidden = true) AuthenticatedPrincipal me,
            CheckoutRequestDto request
    );

    @Operation(summary = "내 주문 목록 페이징 조회", description = "기간 및 상품명 조건으로 주문 목록을 조회합니다.")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_INVALID_RANGE")
    @ApiErrorCodeExample(status = GlobalErrorCode.class, code = "INVALID_INPUT_VALUE", message = "페이지 번호 및 크기가 올바르지 않습니다.")
    @ApiErrorCodeExample(status = GlobalErrorCode.class, code = "INVALID_INPUT_VALUE", message = "검색어는 최대 100자까지 입력 가능합니다.")
    ApiResponse<OrderPageResponseDto> list(
            @Parameter(hidden = true) AuthenticatedPrincipal me,
            @Parameter(description = "조회 기간 (3M, 6M, 1Y, 3Y)") String range,
            @Parameter(description = "상품명 검색어") String productName,
            @Parameter(description = "페이지 번호") int page,
            @Parameter(description = "페이지 크기") int size
    );

    @Operation(summary = "주문 상세 조회", description = "주문 상세 내역 및 배송 상태를 조회합니다.")
    @ApiErrorCodeExample(status = GlobalErrorCode.class, code = "FORBIDDEN")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_NOT_FOUND_ORDER")
    ApiResponse<OrderDetailResponseDto> detail(
            @Parameter(hidden = true) AuthenticatedPrincipal me,
            @Parameter(description = "주문 ID") Long orderId
    );

    @Operation(summary = "취소·반품 내역 조회", description = "신청된 취소 및 반품 클레임 목록을 조회합니다.")
    @ApiErrorCodeExample(status = GlobalErrorCode.class, code = "INVALID_INPUT_VALUE", message = "페이지 번호 및 크기가 올바르지 않습니다.")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_INVALID_REQUEST_TYPE")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_INVALID_REQUEST_STATUS")
    ApiResponse<ClaimHistoryPageResponseDto> listClaims(
            @Parameter(hidden = true) AuthenticatedPrincipal me,
            @Parameter(description = "요청 유형 (CANCEL, RETURN)") String requestType,
            @Parameter(description = "진행 상태") String requestStatus,
            @Parameter(description = "페이지 번호") int page,
            @Parameter(description = "페이지 크기") int size
    );

    @Operation(summary = "반품 접수 사전 정보 조회", description = "반품 신청 전 주문 품목 상태 및 반품 가능 사유 목록을 미리 조회합니다.")
    @ApiErrorCodeExample(status = GlobalErrorCode.class, code = "FORBIDDEN")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_NOT_FOUND_ORDER")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_CONFLICT_ALREADY_CLAIMED")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_INVALID_DELIVERY_STATUS")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_EXPIRED_RETURN_PERIOD")
    ApiResponse<ReturnPreviewResponseDto> returnPreview(
            @Parameter(hidden = true) AuthenticatedPrincipal me,
            @Parameter(description = "주문 ID") Long orderId
    );

    @Operation(summary = "주문 결제 진행 요청", description = "주문을 결제 대기 상태(PAYMENT_PENDING)로 전이합니다.")
    @ApiErrorCodeExample(status = GlobalErrorCode.class, code = "FORBIDDEN")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_NOT_FOUND_ORDER")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_CONFLICT_ALREADY_PAID")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_INVALID_STATUS")
    ApiResponse<PlaceOrderResponseDto> placeOrder(
            @Parameter(hidden = true) AuthenticatedPrincipal me,
            PlaceOrderRequestDto request
    );

    @Operation(summary = "주문 취소 신청", description = "출고 지시 이전 상태인 주문의 전체 취소를 접수합니다.")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_INVALID_REASON_CODE")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_INVALID_REASON_DETAIL")
    @ApiErrorCodeExample(status = GlobalErrorCode.class, code = "FORBIDDEN")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_NOT_FOUND_ORDER")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_CONFLICT_ALREADY_CLAIMED")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_INVALID_STATUS", message = "결제 완료 주문만 취소할 수 있습니다.")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_CONFLICT_RELEASE_STARTED")
    ApiResponse<OrderClaimResponseDto> cancel(
            @Parameter(hidden = true) AuthenticatedPrincipal me,
            @Parameter(description = "주문 ID") Long orderId,
            ClaimRequestDto request
    );

    @Operation(summary = "반품 접수 신청", description = "배송 완료된 주문에 대해 사진 증빙과 함께 반품을 접수합니다.")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_INVALID_REASON_CODE")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_INVALID_REASON_DETAIL")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_MISSING_RETURN_EVIDENCE")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_INVALID_RETURN_EVIDENCE")
    @ApiErrorCodeExample(status = GlobalErrorCode.class, code = "FORBIDDEN")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_NOT_FOUND_ORDER")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_CONFLICT_ALREADY_CLAIMED")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_INVALID_DELIVERY_STATUS")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_INVALID_FRESH_RETURN")
    @ApiErrorCodeExample(status = OrderErrorCode.class, code = "ORD_EXPIRED_RETURN_PERIOD")
    ApiResponse<OrderClaimResponseDto> createReturn(
            @Parameter(hidden = true) AuthenticatedPrincipal me,
            @Parameter(description = "주문 ID") Long orderId,
            ReturnRequestDto request
    );
}