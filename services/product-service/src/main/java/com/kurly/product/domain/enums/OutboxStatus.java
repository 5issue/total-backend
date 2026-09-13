package com.kurly.product.domain.enums;

/** 아웃박스 이벤트의 발행 상태. */
public enum OutboxStatus {

    PENDING,

    PUBLISHED,

    FAILED
}
