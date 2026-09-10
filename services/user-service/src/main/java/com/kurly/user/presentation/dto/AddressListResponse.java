package com.kurly.user.presentation.dto;

import com.kurly.user.domain.entity.DeliveryAddress;

import java.util.List;

public record AddressListResponse(List<AddressResponse> addresses) {

    public static AddressListResponse from(List<DeliveryAddress> addresses) {
        return new AddressListResponse(addresses.stream().map(AddressResponse::from).toList());
    }
}
