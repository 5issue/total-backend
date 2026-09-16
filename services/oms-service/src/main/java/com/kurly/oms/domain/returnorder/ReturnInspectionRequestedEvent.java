package com.kurly.oms.domain.returnorder;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReturnInspectionRequestedEvent(UUID eventId, String routingKey, Long returnId,
                                             Long orderId, LocalDateTime occurredAt) {
    public static ReturnInspectionRequestedEvent from(OmsReturnProcess process) {
        return new ReturnInspectionRequestedEvent(UUID.randomUUID(), "oms.return.inspection-requested",
                process.getReturnId(), process.getOmsOrder().getOrderId(), LocalDateTime.now());
    }
}
