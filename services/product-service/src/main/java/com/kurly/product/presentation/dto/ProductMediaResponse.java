package com.kurly.product.presentation.dto;

import com.kurly.product.infrastructure.entity.ProductMedia;
import com.kurly.product.presentation.support.MediaUrlEncoder;

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
                MediaUrlEncoder.encode(media.getMediaUrl()),
                media.getMediaType(),
                media.getMediaRole(),
                media.getSequence()
        );
    }
}
