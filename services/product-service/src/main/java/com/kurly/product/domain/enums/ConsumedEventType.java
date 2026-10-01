package com.kurly.product.domain.enums;

/** product-service가 소비하는 타 서비스 발행 이벤트의 종류. */
public enum ConsumedEventType {

    INBOUND_COMPLETED,

    OUTBOUND_COMPLETED,

    /** 주문 서비스의 재고 확정 요청. 예약 토큰 하나당 한 번만 처리한다. */
    INVENTORY_CONFIRM_REQUESTED,

    /** 주문 서비스의 재고 복구 요청. 주문 하나당 한 번만 처리한다. */
    INVENTORY_RESTORE_REQUESTED
}
