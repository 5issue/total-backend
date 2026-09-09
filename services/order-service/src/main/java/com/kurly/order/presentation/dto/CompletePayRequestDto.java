package com.kurly.order.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;

public record CompletePayRequestDto(@NotNull @Positive Long paymentId, @NotNull @Positive Long paymentAmount,
                                    @NotNull LocalDateTime paidAt) {
}
