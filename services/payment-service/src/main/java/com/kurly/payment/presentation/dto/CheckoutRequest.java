package com.kurly.payment.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 결제 승인 요청.
 *
 * <p><b>금액은 원 단위 정수다.</b> {@code Long}으로 받고 소수부가 오면 본문 파싱 단계에서 거부한다
 * ({@code spring.jackson.deserialization.accept-float-as-int=false}). 이 설정이 없으면 Jackson이
 * {@code 32000.9}를 조용히 {@code 32000}으로 잘라, 승인 금액과 요청 금액이 어긋난다.
 *
 * <p>{@code amount}는 위변조 검증용이며, 실제 승인 금액은 주문 서비스에서 조회한 값을 기준으로 한다.
 */
public record CheckoutRequest(

        @NotNull(message = "주문 ID는 필수입니다.")
        Long orderId,

        @NotBlank(message = "결제 수단은 필수입니다.")
        @Size(max = 50, message = "결제 수단은 50자를 넘을 수 없습니다.")
        String paymentMethod,

        @NotBlank(message = "결제 인증 토큰은 필수입니다.")
        @Size(max = 255, message = "결제 인증 토큰은 255자를 넘을 수 없습니다.")
        String paymentKey,

        @NotNull(message = "결제 금액은 필수입니다.")
        @Positive(message = "결제 금액은 0보다 커야 합니다.")
        Long amount
) {
}
