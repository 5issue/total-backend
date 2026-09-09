package com.kurly.user.presentation.dto;

import com.kurly.user.domain.enums.AuthProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SyncProfileRequest(
        @NotNull(message = "provider는 필수입니다.")
        AuthProvider provider,

        @NotBlank(message = "providerId는 필수입니다.")
        String providerId
) {
}
