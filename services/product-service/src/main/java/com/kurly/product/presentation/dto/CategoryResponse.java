package com.kurly.product.presentation.dto;

import com.kurly.product.infrastructure.entity.Category;

import com.kurly.product.infrastructure.entity.Category.CategoryType;
import java.util.List;

public record CategoryResponse(
        Long id,
        String name,
        CategoryType type,
        Integer sequence,
        List<CategoryResponse> children
) {
    public static CategoryResponse of(Category category, List<CategoryResponse> children) {
        return new CategoryResponse(category.getId(), category.getName(), category.getType(),
                category.getSequence(), children);
    }
}
