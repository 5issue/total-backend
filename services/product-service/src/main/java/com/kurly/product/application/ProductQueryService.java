package com.kurly.product.application;

import com.kurly.common.exception.BusinessException;
import com.kurly.common.exception.EntityNotFoundException;
import com.kurly.common.exception.GlobalErrorCode;
import com.kurly.product.application.support.HomeLayoutProvider;
import com.kurly.product.domain.dto.ProductSearchCondition;
import com.kurly.product.domain.enums.ProductSortType;
import com.kurly.product.domain.repository.ProductRepository;
import com.kurly.product.infrastructure.entity.Product;
import com.kurly.product.infrastructure.entity.ProductMedia;
import com.kurly.product.infrastructure.entity.ProductMedia.MediaRole;
import com.kurly.product.infrastructure.jpa.ProductMediaJpaRepository;
import com.kurly.product.infrastructure.jpa.ProductSpecJpaRepository;
import com.kurly.product.presentation.dto.HomeResponse;
import com.kurly.product.presentation.dto.HomeResponse.HomeSection;
import com.kurly.product.presentation.dto.HomeResponse.ProductSummaryDto;
import com.kurly.product.presentation.dto.ProductDetailResponse;
import com.kurly.product.presentation.dto.ProductFilterResponse;
import com.kurly.product.presentation.dto.ProductMediaResponse;
import com.kurly.product.presentation.dto.ProductSpecResponse;
import com.kurly.product.presentation.dto.ProductSummaryResponse;
import com.kurly.product.presentation.dto.ProductUnitResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductQueryService {
    private final ProductRepository productRepository;
    private final ProductMediaJpaRepository productMediaRepository;
    private final ProductSpecJpaRepository productSpecRepository;
    private final HomeLayoutProvider homeLayoutProvider;
    private final ProductFilterService productFilterService;

    @Cacheable(value = "homeDashboard", key = "'main'")
    public HomeResponse getHomeRecommendations() {
        HomeSection quickMenuSection = new HomeSection(
                "QUICK_MENU",
                "빠른 메뉴",
                homeLayoutProvider.getQuickMenus(),
                null
        );

        return new HomeResponse(List.of(quickMenuSection,
                createHomeSection(
                        "TOP_LIKED_PRODUCTS",
                        "지금 가장 많이 담는 특가",
                        productRepository.findTopLikedProducts(20)
                ),
                createHomeSection(
                        "TOP_DISCOUNTED_PRODUCTS",
                        "놓치면 후회할 가격",
                        productRepository.findTopDiscountedProducts(20)
                ),
                createHomeSection(
                        "TOP_REPURCHASE_PRODUCTS",
                        "재구매만 1만회 이상 기록",
                        productRepository.findTopRepurchaseProducts(20)
                )));

    }

    public Slice<ProductSummaryResponse> getProducts(ProductSearchCondition condition,
                                                     ProductSortType sortType, int page, int size) {
        if (condition.categoryId() == null && condition.normalizedKeyword() == null) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE, "categoryId 또는 keyword 중 하나는 필요합니다.");
        }
        Pageable pageable = PageRequest.of(page, size, sortType.toSort());
        Slice<Product> products = productRepository.searchProducts(condition, pageable);

        Map<Long, String> thumbnailByProductId = getThumbnailMap(products.getContent());
        return products.map(product ->
                ProductSummaryResponse.of(product, thumbnailByProductId.get(product.getId())));
    }

    public ProductDetailResponse getProductDetail(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("상품을 찾을 수 없습니다. productId=" + productId));

        ProductSpecResponse specResponse = productSpecRepository.findByProductId(productId)
                .map(ProductSpecResponse::from)
                .orElse(null);

        List<ProductMediaResponse> mediaResponses = productMediaRepository
                .findByProductIdOrderBySequenceAsc(productId)
                .stream()
                .map(ProductMediaResponse::from)
                .toList();

        List<ProductUnitResponse> units = productRepository.findByParentId(productId)
                .stream()
                .map(ProductUnitResponse::from)
                .toList();

        return ProductDetailResponse.of(product, specResponse, mediaResponses, units);
    }

    public ProductFilterResponse getFilters(Long categoryId, String keyword) {
        boolean hasCategory = (categoryId != null);
        boolean hasKeyword = (keyword != null && !keyword.isBlank());

        if (hasCategory && hasKeyword) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE, "카테고리 필터와 검색 키워드는 동시에 적용할 수 없습니다.");
        }
        if (!hasCategory && !hasKeyword) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE, "카테고리 ID나 검색 키워드 중 하나는 반드시 입력되어야 합니다.");
        }

        if (hasCategory) {
            return productFilterService.buildFilterByCategory(categoryId);
        }

        return productFilterService.buildFilterByKeyword(keyword);
    }

    private HomeSection createHomeSection(String sectionId, String title, List<Product> products) {
        Map<Long, String> thumbnailByProductId = getThumbnailMap(products);

        List<ProductSummaryDto> itemDtos = products.stream()
                .map(product -> ProductSummaryDto.of(product, thumbnailByProductId.get(product.getId())))
                .toList();

        return new HomeSection(sectionId, title, null, itemDtos);
    }

    private Map<Long, String> getThumbnailMap(List<Product> products) {
        List<Long> productIds = products.stream().map(Product::getId).toList();
        if (productIds.isEmpty()) {
            return Map.of();
        }

        return productMediaRepository.findByProductIdInAndMediaRole(productIds, MediaRole.THUMBNAIL)
                .stream()
                .collect(Collectors.toMap(
                        media -> media.getProduct().getId(),
                        ProductMedia::getMediaUrl,
                        (first, second) -> first));
    }
}
