package com.kurly.order.infrastructure.dto;


public record AddressResponse(
        Long addressId,
        String addressName,
        String recipientName,
        String recipientPhone,
        String zipCode,
        String address,
        String detailAddress
) {
}
