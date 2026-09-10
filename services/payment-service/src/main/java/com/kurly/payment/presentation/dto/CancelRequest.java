package com.kurly.payment.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 결제 취소 요청. */
public record CancelRequest(

        @NotBlank(message = "취소 사유는 필수입니다.")
        @Size(max = 255, message = "취소 사유는 255자를 넘을 수 없습니다.")
        String cancelReason
) {
}
