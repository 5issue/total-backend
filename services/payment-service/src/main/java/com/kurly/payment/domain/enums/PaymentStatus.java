package com.kurly.payment.domain.enums;

/**
 * 결제 상태.
 *
 * <p>값을 추가·삭제하면 {@code payments_chk_1} CHECK 제약도 함께 바꾸는 마이그레이션이 필요하다.
 * {@code ddl-auto: update}는 CHECK 제약을 갱신하지 않는다.
 */
public enum PaymentStatus {

    /** 결제 행은 만들었으나 PG 승인 전. */
    REQUESTED,

    /** PG 승인 완료. */
    SUCCESS,

    /** PG 승인 실패. 같은 주문으로 재시도하면 새 결제 행이 생긴다. */
    FAILED,

    /** 전액 취소. */
    CANCELED,

    /** 부분 취소. 남은 금액이 있어 완전히 종결되지 않은 상태다. */
    PARTIAL_CANCELED;

    /** 취소를 시도할 수 있는 상태인지. 실제 가능 여부는 취소 금액까지 봐야 한다. */
    public boolean isCancellable() {
        return this == SUCCESS || this == PARTIAL_CANCELED;
    }
}
