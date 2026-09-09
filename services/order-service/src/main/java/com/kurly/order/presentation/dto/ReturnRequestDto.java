package com.kurly.order.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReturnRequestDto(
        @NotBlank String reasonCode,
        @Size(max = 500) String reasonDetail,
        @Size(max = 5) List<@Valid ReturnAttachmentRequestDto> attachments
) {
    public List<ReturnAttachmentRequestDto> attachmentsOrEmpty() {
        return attachments == null ? List.of() : List.copyOf(attachments);
    }
}
