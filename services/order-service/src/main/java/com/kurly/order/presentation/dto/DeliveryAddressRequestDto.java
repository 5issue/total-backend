package com.kurly.order.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DeliveryAddressRequestDto(@NotNull @Positive Long addressId) {}
