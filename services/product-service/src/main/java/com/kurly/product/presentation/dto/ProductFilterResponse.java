package com.kurly.product.presentation.dto;

import java.util.List;

public record ProductFilterResponse(
        long totalCount,
        List<FilterGroupDto> filterGroups
) {
    public record FilterGroupDto (
            String filterId,
            String title,
            List<FilterItemDto> items
    ) {}

    public record FilterItemDto(
            String label,
            String value,
            long count
    ) {}
}
