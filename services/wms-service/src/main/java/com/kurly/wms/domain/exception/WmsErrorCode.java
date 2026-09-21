package com.kurly.wms.domain.exception;

import com.kurly.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum WmsErrorCode implements ErrorCode {

    NO_AVAILABLE_LOCATION(HttpStatus.CONFLICT, "WMS409", "적치 가능한 가용 로케이션이 부족합니다."),
    INVALID_STOCK_MOVEMENT_STATUS(HttpStatus.CONFLICT, "WMS4092", "대기 중인 작업 지시가 아닙니다."),
    INVALID_INBOUND_ITEM_STATUS(HttpStatus.CONFLICT, "WMS4093", "대기 중인 입고 상세가 아닙니다."),
    INSUFFICIENT_INVENTORY(HttpStatus.CONFLICT, "WMS4094", "재고가 부족합니다."),
    INVALID_INVENTORY_LOCATION(HttpStatus.CONFLICT, "WMS4095", "재고의 로케이션과 요청된 fromLocationId가 일치하지 않습니다.")
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
