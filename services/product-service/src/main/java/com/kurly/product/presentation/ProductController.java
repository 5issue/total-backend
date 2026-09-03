package com.kurly.product.presentation;

import com.kurly.common.response.ApiResponse;
import com.kurly.product.application.ProductQueryService;
import com.kurly.product.domain.enums.PriceBand;
import com.kurly.product.domain.dto.ProductSearchCondition;
import com.kurly.product.infrastructure.entity.ProductSpec.StorageType;
import com.kurly.product.presentation.dto.HomeResponse;
import com.kurly.product.presentation.dto.ProductDetailResponse;
import com.kurly.product.presentation.dto.ProductFilterResponse;
import com.kurly.product.domain.enums.ProductSortType;
import com.kurly.product.presentation.dto.ProductSummaryResponse;
import org.springframework.data.domain.Slice;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductQueryService productQueryService;

    public ProductController(ProductQueryService productQueryService) {
        this.productQueryService = productQueryService;
    }

    @GetMapping("/home-recommendations")
    public ApiResponse<HomeResponse> getHomeRecommendations() {
        return ApiResponse.success(productQueryService.getHomeRecommendations());
    }

    @GetMapping //가격, 브렌드, 포장 타입
    public ApiResponse<Slice<ProductSummaryResponse>> getProducts(
            @RequestParam Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "RECOMMENDED") ProductSortType sort,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String price,
            @RequestParam(required = false) StorageType storageType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ProductSearchCondition condition = new ProductSearchCondition(categoryId, keyword, brand, PriceBand.from(price), storageType);
        return ApiResponse.success(productQueryService.getProducts(condition, sort, page, size));
    }

    @GetMapping("/filters/{categoryId}")
    public ApiResponse<ProductFilterResponse> getFilters(@PathVariable Long categoryId) {
        return ApiResponse.success(productQueryService.getFilters(categoryId));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductDetailResponse> getProductDetail(@PathVariable Long productId) {
        return ApiResponse.success(productQueryService.getProductDetail(productId));
    }
}
