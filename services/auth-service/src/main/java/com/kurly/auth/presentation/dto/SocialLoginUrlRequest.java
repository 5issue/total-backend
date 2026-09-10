package com.kurly.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record SocialLoginUrlRequest(
        @NotBlank(message = "redirectUri는 필수입니다.")
        String redirectUri
) {
}
