package com.kurly.order.domain.order;

public enum OrderStatus {
    CHECKOUT_CREATED,
    PENDING_PAYMENT,
    PAID,
    CANCELLED,
    CANCELLED_EXPIRED,
    REFUNDED
}
