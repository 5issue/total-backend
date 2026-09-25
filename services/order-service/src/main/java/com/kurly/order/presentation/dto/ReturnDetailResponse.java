package com.kurly.order.presentation.dto;

import com.kurly.order.domain.claim.OrderClaim;
import com.kurly.order.domain.claim.RefundAttachment;
import com.kurly.order.domain.order.OrderItem;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public record ReturnDetailResponse(
        Long returnId,
        Long orderId,
        Long paymentId,
        String status,
        String reasonCode,
        String reasonDetail,
        List<AttachmentInfo> attachments,
        List<ItemGroup> itemGroups,
        Long refundAmount,
        LocalDateTime requestedAt
) {
    public static ReturnDetailResponse from(OrderClaim claim) {
        List<ItemGroup> groups = claim.getOrder().getItems().stream()
                .collect(Collectors.groupingBy(item -> item.getStorageType().name()))
                .entrySet().stream()
                .map(entry -> new ItemGroup(
                        entry.getKey(),
                        entry.getValue().stream()
                                .map(ItemInfo::from)
                                .toList()
                ))
                .toList();

        return new ReturnDetailResponse(
                claim.getId(),
                claim.getOrder().getId(),
                claim.getOrder().getPaymentId(),
                claim.getStatus().name(),
                claim.getReasonCode(),
                claim.getReasonDetail(),
                claim.getAttachments().stream()
                        .map(AttachmentInfo::from)
                        .toList(),
                groups,
                claim.getExpectedRefundAmount(),
                claim.getRequestedAt()
        );
    }

    public record ItemGroup(
            String storageType,
            List<ItemInfo> items
    ) {
    }

    public record ItemInfo(
            Long productId,
            Long skuId,
            Integer quantity
    ) {
        public static ItemInfo from(OrderItem item) {
            return new ItemInfo(
                    item.getProductId(),
                    item.getSkuId(),
                    item.getQuantity()
            );
        }
    }

    public record AttachmentInfo(
            String s3Bucket,
            String s3ObjectKey,
            String originalFileName
    ) {
        public static AttachmentInfo from(RefundAttachment attachment) {
            return new AttachmentInfo(
                    attachment.getS3Bucket(),
                    attachment.getS3ObjectKey(),
                    attachment.getOriginalFileName()
            );
        }
    }
}