package com.kurly.oms.infrastructure.messaging;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record WmsReturnInspectedMessage(
        UUID eventId,
        Long omsReturnId,
        Long omsOrderId,
        String inspectionResult,
        String faultType,
        List<Long> approvedItemIds,
        List<Long> rejectedItemIds,
        String wmsNote,
        LocalDateTime inspectedAt
) {
}