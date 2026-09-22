package com.kurly.oms.domain.common;

import com.kurly.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OmsErrorCode implements ErrorCode {

    // 400 BAD_REQUEST
    OMS_INVALID_STATUS(HttpStatus.BAD_REQUEST, "현재 상태에서 처리할 수 없습니다."),

    // 404 NOT_FOUND
    OMS_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문 정보를 찾을 수 없습니다."),
    OMS_RETURN_NOT_FOUND(HttpStatus.NOT_FOUND, "반품 정보를 찾을 수 없습니다."),

    // 409 CONFLICT
    OMS_CONFLICT_TEMPERATURE(HttpStatus.CONFLICT, "콜드체인(냉장/냉동) 품목만 자체폐기 승인이 가능합니다.");

    // 422 UNPROCESSABLE_CONTENT

    // 502 BAD_GATEWAY

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
