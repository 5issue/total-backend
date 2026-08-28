package com.kurly.order.domain.outbox;

public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
