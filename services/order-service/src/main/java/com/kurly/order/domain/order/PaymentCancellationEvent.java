package com.kurly.order.domain.order;

public record PaymentCancellationEvent(Long paymentId, OrderEvent inventoryRestoreEvent) {
    public static PaymentCancellationEvent of(Order order) {
        return new PaymentCancellationEvent(
                order.getPaymentId(),
                OrderEvent.of("order.canceled.inventory-restore", order)
        );
    }
}
