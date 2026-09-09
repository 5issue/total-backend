package com.kurly.user.presentation.dto;

public record CreateAddressResponse(Long addressId, boolean success) {

    public static CreateAddressResponse of(Long addressId) {
        return new CreateAddressResponse(addressId, true);
    }
}
