package com.kurly.order.presentation.dto;

import com.kurly.order.domain.common.StorageType;

import java.util.List;

public record ReturnPreviewResponseDto(
        Long orderId,
        boolean returnable,
        StorageType temperaturePolicy,
        List<ReasonOption> reasonOptions,
        RefundPreview refundPreview,
        ReturnPolicy returnPolicy
) {
    public record ReasonOption(String code, String displayName, boolean attachmentRequired) {
    }

    public record RefundPreview(Long paymentAmount, Long deductionAmount, Long expectedRefundAmount) {
    }

    public record ReturnPolicy(boolean collectionRequired, String guideMessage) {
    }
}
