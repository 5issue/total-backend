package com.kurly.product.application.event;

/**
 * 아웃박스 row가 커밋됐음을 알리는 애플리케이션 이벤트.
 */
public record OutboxRecordedEvent(Long outboxId) {
}
