package com.kurly.oms.presentation.dto;

import com.kurly.oms.domain.returnorder.OmsReturnStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ReturnJudgementResponse(
        Long omsOrderId,
        OmsReturnStatus returnStatus,
        List<Long> coldchainApprovedIds,
        List<Long> logisticsApprovedIds,
        List<Long> rejectedItemIds,
        LocalDateTime judgedAt
) {
}