package com.kurly.payment.domain.enums;

/** 결제 취소 시도의 상태. 취소는 실패할 수 있고 실패 건은 배치가 재시도한다. */
public enum CancelStatus {

    /** PG에 취소를 요청했으나 아직 결과를 받지 못함. */
    REQUESTED,

    SUCCESS,

    /** PG 취소 실패. {@code failure_reason}에 사유가 남고 재시도 대상이 된다. */
    FAILED
}
