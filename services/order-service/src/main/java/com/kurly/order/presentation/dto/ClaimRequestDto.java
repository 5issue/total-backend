package com.kurly.order.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClaimRequestDto(@NotBlank String reasonCode, @Size(max = 500) String reasonDetail) {
}
