package com.kurly.order.domain.common;

import com.kurly.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {
    ORD_INVALID_STATUS(HttpStatus.BAD_REQUEST, "결제를 진행할 수 없는 주문 상태입니다."),
    ORD_INVALID_REASON_CODE(HttpStatus.BAD_REQUEST, "올바르지 않은 사유 코드입니다."),
    ORD_INVALID_REASON_DETAIL(HttpStatus.BAD_REQUEST, "상세 사유가 올바르지 않습니다."),
    ORD_INVALID_CART_ITEMS(HttpStatus.BAD_REQUEST, "주문할 장바구니 항목이 올바르지 않습니다."),
    ORD_INVALID_REQUEST_TYPE(HttpStatus.BAD_REQUEST, "올바르지 않은 요청 유형입니다."),
    ORD_INVALID_REQUEST_STATUS(HttpStatus.BAD_REQUEST, "올바르지 않은 진행 상태값입니다."),
    ORD_INVALID_PAGE(HttpStatus.BAD_REQUEST, "페이지 번호 및 크기가 올바르지 않습니다."),
    ORD_NOT_FOUND_ADDRESS(HttpStatus.BAD_REQUEST, "배송지를 먼저 설정해 주세요."),
    ORD_FORBIDDEN_OWNERSHIP(HttpStatus.FORBIDDEN, "해당 주문에 접근할 권한이 없습니다."),
    ORD_NOT_FOUND_ORDER(HttpStatus.NOT_FOUND, "주문 정보를 찾을 수 없습니다."),
    ORD_CONFLICT_ALREADY_PAID(HttpStatus.CONFLICT, "이미 결제가 완료된 주문입니다."),
    ORD_CONFLICT_ALREADY_CLAIMED(HttpStatus.CONFLICT, "이미 접수된 취소 또는 반품 신청이 있습니다."),
    ORD_EXPIRED_TIMEOUT(HttpStatus.CONFLICT, "결제 유효시간 초과로 이미 만료된 주문입니다."),
    ORD_INVALID_DELIVERY_STATUS(HttpStatus.UNPROCESSABLE_ENTITY, "배송 완료 주문만 반품을 신청할 수 있습니다."),
    ORD_EXPIRED_RETURN_PERIOD(HttpStatus.UNPROCESSABLE_ENTITY, "반품 신청 가능 기간이 지났습니다."),
    ORD_RESTRICTED_FRESH_RETURN(HttpStatus.UNPROCESSABLE_ENTITY, "냉장·냉동 상품은 단순 변심으로 반품할 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
