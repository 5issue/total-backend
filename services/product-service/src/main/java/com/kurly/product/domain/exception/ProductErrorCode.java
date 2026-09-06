package com.kurly.product.domain.exception;

import com.kurly.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProductErrorCode implements ErrorCode {

    OUT_OF_STOCK(HttpStatus.BAD_REQUEST, "PROD400", "재고가 부족합니다."),
    INVENTORY_UNAVAILABLE(HttpStatus.INTERNAL_SERVER_ERROR, "PROD500", "재고 정보를 처리할 수 없습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
