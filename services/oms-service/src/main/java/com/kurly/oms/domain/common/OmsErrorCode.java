package com.kurly.oms.domain.common;

import com.kurly.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OmsErrorCode implements ErrorCode {

    // 400 BAD_REQUEST

    // 404 NOT_FOUND
    OMS_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문 정보를 찾을 수 없습니다."),
    OMS_RETURN_NOT_FOUND(HttpStatus.NOT_FOUND, "반품 정보를 찾을 수 없습니다."),

    // 409 CONFLICT
    OMS_ALREADY_PROCESSING(HttpStatus.CONFLICT, "취소가 불가능한 주문입니다.");

    // 422 UNPROCESSABLE_CONTENT

    // 502 BAD_GATEWAY

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
