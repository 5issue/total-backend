package com.kurly.order.domain.order;

public enum OrderStatus {
    CHECKOUT_CREATED,
    PENDING_PAYMENT,
    PAID,
    CANCEL_PROCESSING,
    CANCELLED,
    EXPIRED,
    RETURN_REQUESTED,
    REFUNDED
}
