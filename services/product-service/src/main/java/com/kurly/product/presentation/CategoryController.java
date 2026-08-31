package com.kurly.product.presentation;

import com.kurly.common.response.ApiResponse;
import com.kurly.product.application.CategoryService;
import com.kurly.product.infrastructure.entity.Category.CategoryType;
import com.kurly.product.presentation.dto.CategoryResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/categories")
    public ApiResponse<Map<CategoryType, List<CategoryResponse>>> getCategories() {
        return ApiResponse.success(categoryService.getCategoryTree());
    }
}
