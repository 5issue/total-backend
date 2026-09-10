package com.kurly.product.presentation.dto;

import com.kurly.product.infrastructure.entity.ProductMedia;

public record ProductMediaResponse(
        Long id,
        String mediaUrl,
        ProductMedia.MediaType mediaType,
        ProductMedia.MediaRole mediaRole,
        Integer sequence
) {
    public static ProductMediaResponse from(ProductMedia media) {
        return new ProductMediaResponse(
                media.getId(),
                media.getMediaUrl(),
                media.getMediaType(),
                media.getMediaRole(),
                media.getSequence()
        );
    }
}
