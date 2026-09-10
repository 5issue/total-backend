package com.kurly.payment.domain.enums;

/**
 * 멱등키 처리 상태.
 *
 * <p>처리 <b>전에</b> {@code IN_PROGRESS}로 먼저 기록해야 "동일 요청이 처리 중"(409)을 판별할 수 있다.
 * 완료된 응답만 저장하면 진행 중인 요청과 아예 없는 요청을 구분하지 못한다.
 */
public enum IdempotencyStatus {

    IN_PROGRESS,

    /** 응답까지 확정됨. 같은 키로 다시 오면 저장된 응답을 그대로 재생한다. */
    COMPLETED
}
