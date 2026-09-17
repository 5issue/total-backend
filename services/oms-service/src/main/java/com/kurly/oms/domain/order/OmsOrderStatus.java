package com.kurly.oms.domain.order;

public enum OmsOrderStatus {
    ORDER_RECEIVED,
    PROCESSING,
    STOCK_REQUESTED,
    RELEASE_INSTRUCTED,
    FULFILLED,
    CANCELLED,
    INSPECTION_REQUESTED,
    RETURN_REQUESTED,
    REFUND_APPROVED
}