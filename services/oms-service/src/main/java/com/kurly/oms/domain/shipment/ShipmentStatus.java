package com.kurly.oms.domain.shipment;

public enum ShipmentStatus {
    SHIPMENT_CREATED,
    CAPA_APPROVED,
    PENDING_ROLLOVER,
    STOCK_REQUESTED,
    STOCK_ALLOCATED,
    RELEASE_INSTRUCTED,
    FULFILLED,
    CANCELLED
}