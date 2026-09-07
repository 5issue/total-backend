package com.kurly.order.presentation.dto;

import com.kurly.order.domain.claim.ClaimStatus;
import com.kurly.order.domain.claim.ClaimType;
import com.kurly.order.domain.claim.OrderClaim;
import com.kurly.order.domain.claim.RefundStatus;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;

public record ClaimHistoryPageResponseDto(long total, int page, int size, List<History> histories) {
    public static ClaimHistoryPageResponseDto from(Page<OrderClaim> result, int page, int size) {
        return new ClaimHistoryPageResponseDto(result.getTotalElements(), page, size,
                result.getContent().stream().map(History::from).toList());
    }

    public record History(ClaimType requestType, Long requestId, Long orderId, String orderNo,
                          ClaimStatus requestStatus, RefundStatus refundStatus, Long refundAmount,
                          LocalDateTime requestedAt, LocalDateTime completedAt, List<OrderItemResponseDto> items) {
        static History from(OrderClaim claim) {
            return new History(claim.getClaimType(), claim.getId(), claim.getOrder().getId(), claim.getOrder().getOrderNo(),
                    claim.getStatus(), claim.getRefundStatus(), claim.getRefundAmount(), claim.getRequestedAt(),
                    claim.getCompletedAt(), claim.getOrder().getItems().stream().map(OrderItemResponseDto::from).toList());
        }
    }
}
