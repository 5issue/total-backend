package com.kurly.payment.domain.enums;

/** 재시도 작업의 상태. */
public enum RetryStatus {

    /** 다음 시도를 기다리는 중. {@code next_retry_at}이 지나면 배치가 집어간다. */
    PENDING,

    SUCCESS,

    /** 재시도 상한을 넘겨 중단됨. 사람이 봐야 하는 상태다. */
    FAILED
}
