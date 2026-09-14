package com.kurly.order.domain.order;

import com.kurly.order.domain.common.StorageType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SalesOrderCreatedEvent(
        UUID eventId,
        String routingKey,
        Long orderId,
        String orderNo,
        Long memberId,
        String reservationToken,
        Long paymentId,
        Long paidAmount,
        LocalDateTime paidAt,
        DeliveryAddress deliveryAddress,
        List<Item> items
) {
    public static SalesOrderCreatedEvent of(Order order, OrderDeliveryInfo deliveryInfo) {
        return new SalesOrderCreatedEvent(
                UUID.randomUUID(),
                "sales.order.created",
                order.getId(),
                order.getOrderNo(),
                order.getMemberId(),
                order.getInventoryReservationToken(),
                order.getPaymentId(),
                order.getPaymentAmount(),
                order.getPaidAt(),
                new DeliveryAddress(
                        deliveryInfo.getRecipientName(),
                        deliveryInfo.getPhone(),
                        deliveryInfo.getZipCode(),
                        deliveryInfo.getAddress(),
                        deliveryInfo.getAddressDetail()
                ),
                order.getItems().stream()
                        .map(item -> new Item(
                                item.getProductId(),
                                item.getSkuId(),
                                item.getQuantity(),
                                item.getStorageType()
                        ))
                        .toList()
        );
    }

    public record DeliveryAddress(
            String recipientName,
            String phone,
            String zipCode,
            String address,
            String addressDetail
    ) {
    }

    public record Item(
            Long productId,
            Long skuId,
            Integer quantity,
            StorageType storageType
    ) {
    }
}
