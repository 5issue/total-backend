package com.kurly.order.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ReturnAttachmentRequestDto(
        @NotBlank @Size(max = 500) String objectKey,
        @Size(max = 255) String originalFileName,
        @NotBlank @Pattern(regexp = "image/(jpeg|png|webp)") String contentType,
        @Positive @Max(10_485_760) Long fileSize
) {
}
