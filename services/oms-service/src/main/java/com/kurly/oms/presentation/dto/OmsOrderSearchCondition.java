package com.kurly.oms.presentation.dto;

import java.time.LocalDateTime;

public record OmsOrderSearchCondition(
        String orderNo,
        String status,
        Long regionId,
        Long centerId,
        LocalDateTime startAt,
        LocalDateTime endAt
) {
}