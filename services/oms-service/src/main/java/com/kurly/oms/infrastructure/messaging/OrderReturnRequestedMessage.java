package com.kurly.oms.infrastructure.messaging;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrderReturnRequestedMessage(
        UUID eventId,
        Long orderId,
        Long memberId,
        LocalDateTime occurredAt
) {
}
