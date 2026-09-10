package com.kurly.order.domain.common;

import com.kurly.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {

    // 400 BAD_REQUEST
    ORD_INVALID_STATUS(HttpStatus.BAD_REQUEST, "결제를 진행할 수 없는 주문 상태입니다."),
    ORD_INVALID_REASON_CODE(HttpStatus.BAD_REQUEST, "올바르지 않은 사유 코드입니다."),
    ORD_INVALID_REASON_DETAIL(HttpStatus.BAD_REQUEST, "상세 사유가 올바르지 않습니다."),
    ORD_INVALID_RANGE(HttpStatus.BAD_REQUEST, "조회 기간 설정이 올바르지 않습니다."),
    ORD_INVALID_REQUEST_TYPE(HttpStatus.BAD_REQUEST, "올바르지 않은 요청 유형입니다."),
    ORD_INVALID_REQUEST_STATUS(HttpStatus.BAD_REQUEST, "올바르지 않은 진행 상태값입니다."),
    ORD_INVALID_CART_ITEMS(HttpStatus.BAD_REQUEST, "주문할 장바구니 항목이 올바르지 않습니다."),
    ORD_INVALID_PAYMENT_AMOUNT(HttpStatus.BAD_REQUEST, "주문 금액과 결제 금액이 일치하지 않습니다."),
    ORD_MISSING_RETURN_EVIDENCE(HttpStatus.BAD_REQUEST, "사진 증빙이 필요한 반품 사유입니다."),
    ORD_INVALID_RETURN_EVIDENCE(HttpStatus.BAD_REQUEST, "유효하지 않은 반품 사진입니다."),
    ORD_INVALID_PAGE(HttpStatus.BAD_REQUEST, "페이지 번호 및 크기가 올바르지 않습니다."),

    // 404 NOT_FOUND
    ORD_NOT_FOUND_ADDRESS(HttpStatus.NOT_FOUND, "배송지를 먼저 설정해 주세요."),
    ORD_NOT_FOUND_ORDER(HttpStatus.NOT_FOUND, "주문 정보를 찾을 수 없습니다."),

    // 409 CONFLICT
    ORD_INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "주문 상품의 재고가 부족합니다."),
    ORD_CONFLICT_ALREADY_PAID(HttpStatus.CONFLICT, "이미 결제가 완료된 주문입니다."),
    ORD_CONFLICT_ALREADY_CLAIMED(HttpStatus.CONFLICT, "이미 접수된 취소 또는 반품 신청이 있습니다."),
    ORD_CONFLICT_RELEASE_STARTED(HttpStatus.CONFLICT, "이미 출고 처리가 시작되어 취소할 수 없습니다."),
    ORD_EXPIRED_PAYMENT_TIMEOUT(HttpStatus.CONFLICT, "결제 유효시간 초과로 이미 만료된 주문입니다."),

    // 422 UNPROCESSABLE_CONTENT
    ORD_INVALID_DELIVERY_STATUS(HttpStatus.UNPROCESSABLE_CONTENT, "배송 완료 주문만 반품을 신청할 수 있습니다."),
    ORD_EXPIRED_RETURN_PERIOD(HttpStatus.UNPROCESSABLE_CONTENT, "반품 신청 가능 기간이 지났습니다."),
    ORD_INVALID_FRESH_RETURN(HttpStatus.UNPROCESSABLE_CONTENT, "냉장·냉동 상품은 단순 변심으로 반품할 수 없습니다."),

    // 502 BAD_GATEWAY
    ORD_INCOMPLETE_PRODUCT_RESPONSE(HttpStatus.BAD_GATEWAY, "상품 정보를 완전하게 조회하지 못했습니다.");
    
    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
