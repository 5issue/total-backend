package com.kurly.payment.domain.enums;

/** 아웃박스 이벤트의 발행 상태. */
public enum OutboxStatus {

    /** 아직 발행되지 않음. 발행 워커의 처리 대상. */
    PENDING,

    PUBLISHED,

    /** 발행에 반복 실패해 중단됨. 사람이 봐야 하는 상태다. */
    FAILED
}
