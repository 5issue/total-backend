package com.kurly.oms.presentation.dto;

import com.kurly.oms.domain.returnorder.ReturnDecision;

import java.util.List;

public record ReturnJudgementRequest(
        String adminNote,
        List<ItemJudgement> judgements
) {
    public record ItemJudgement(
            Long omsOrderItemId,
            ReturnDecision decision,
            String rejectReason
    ) {
    }
}
