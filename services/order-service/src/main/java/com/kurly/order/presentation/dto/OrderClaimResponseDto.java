package com.kurly.order.presentation.dto;

import com.kurly.order.domain.claim.ClaimStatus;
import com.kurly.order.domain.claim.ClaimType;
import com.kurly.order.domain.claim.OrderClaim;

import java.time.LocalDateTime;

public record OrderClaimResponseDto(Long claimId, Long orderId, ClaimType claimType, ClaimStatus status,
                                    Long expectedRefundAmount, LocalDateTime requestedAt) {
    public static OrderClaimResponseDto from(OrderClaim claim) {
        return new OrderClaimResponseDto(claim.getId(), claim.getOrder().getId(), claim.getClaimType(),
                claim.getStatus(), claim.getExpectedRefundAmount(), claim.getRequestedAt());
    }
}
