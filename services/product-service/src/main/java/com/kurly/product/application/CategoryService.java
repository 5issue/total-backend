package com.kurly.product.application;

import com.kurly.product.domain.repository.CategoryRepository;
import com.kurly.product.infrastructure.entity.Category;
import com.kurly.product.infrastructure.entity.Category.CategoryType;
import com.kurly.product.presentation.dto.CategoryResponse;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Cacheable(value = "categoryTree", key = "'tree'")
    public Map<CategoryType, List<CategoryResponse>> getCategoryTree() {
        List<Category> categories = categoryRepository.findAll();
        Map<Long, List<Category>> childrenByParentId = categories.stream()
                .filter(category -> category.getParentId() != null)
                .collect(Collectors.groupingBy(Category::getParentId));

        return categories.stream()
                .filter(category -> category.getParentId() == null)
                .sorted(Comparator.comparing(Category::getSequence))
                .map(category -> toResponse(category, childrenByParentId))
                .collect(Collectors.groupingBy(CategoryResponse::type));
    }

    private CategoryResponse toResponse(Category category, Map<Long, List<Category>> childrenByParentId) {
        List<CategoryResponse> children = childrenByParentId
                .getOrDefault(category.getId(), List.of())
                .stream()
                .sorted(Comparator.comparing(Category::getSequence))
                .map(child -> toResponse(child, childrenByParentId))
                .toList();
        return CategoryResponse.of(category, children);
    }
}
