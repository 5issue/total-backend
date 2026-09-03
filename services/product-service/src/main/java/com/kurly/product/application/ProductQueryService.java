package com.kurly.product.application;

import com.kurly.common.exception.EntityNotFoundException;
import com.kurly.product.application.support.FilterLayoutProvider;
import com.kurly.product.application.support.HomeLayoutProvider;
import com.kurly.product.infrastructure.entity.Product;
import com.kurly.product.infrastructure.entity.ProductMedia;
import com.kurly.product.infrastructure.entity.ProductMedia.MediaRole;
import com.kurly.product.infrastructure.entity.ProductSpec.StorageType;
import com.kurly.product.infrastructure.jpa.ProductMediaJpaRepository;
import com.kurly.product.domain.repository.ProductRepository;
import com.kurly.product.domain.dto.ProductSearchCondition;
import com.kurly.product.infrastructure.jpa.ProductSpecJpaRepository;
import com.kurly.product.infrastructure.jpa.ProductSpecJpaRepository.StorageTypeCount;
import com.kurly.product.presentation.dto.HomeResponse;
import com.kurly.product.presentation.dto.HomeResponse.HomeSection;
import com.kurly.product.presentation.dto.HomeResponse.ProductSummaryDto;
import com.kurly.product.domain.enums.PriceBand;
import com.kurly.product.presentation.dto.ProductDetailResponse;
import com.kurly.product.presentation.dto.ProductFilterResponse;
import com.kurly.product.presentation.dto.ProductFilterResponse.FilterGroupDto;
import com.kurly.product.presentation.dto.ProductFilterResponse.FilterItemDto;
import com.kurly.product.domain.enums.ProductSortType;
import com.kurly.product.presentation.dto.ProductMediaResponse;
import com.kurly.product.presentation.dto.ProductSpecResponse;
import com.kurly.product.presentation.dto.ProductSummaryResponse;
import com.kurly.product.presentation.dto.ProductUnitResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductQueryService {
    private final ProductRepository productRepository;
    private final ProductMediaJpaRepository productMediaRepository;
    private final ProductSpecJpaRepository productSpecRepository;
    private final HomeLayoutProvider homeLayoutProvider;
    private final FilterLayoutProvider filterLayoutProvider;

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
//        return null;
    }

    @Cacheable(value = "productFilters", key = "#categoryId")
    public ProductFilterResponse getFilters(Long categoryId) {
        // 해당 카테고리(+하위 카테고리)에 속한 GROUP 상품 전체를 기준으로 필터 후보를 만든다.
        List<Product> products = productRepository.findProductsByCategoryId(categoryId);
        List<Long> groupIds = products.stream().map(Product::getId).toList();
        // product_spec 은 UNIT 에만 있으므로, GROUP 의 자식 UNIT spec 을 기준으로 집계한다.
        List<StorageTypeCount> storageTypeCounts = groupIds.isEmpty()
                ? List.of()
                : productSpecRepository.countStorageTypesByGroupIdIn(groupIds);

        List<FilterGroupDto> filterGroups = new ArrayList<>();
        // 정렬 필터 (상품 집합과 무관하게 항상 노출)
        filterGroups.add(new FilterGroupDto("sort", "정렬", filterLayoutProvider.getSortFilters()));
        // 판매업체(브랜드) 필터
        addIfNotEmpty(filterGroups, buildBrandFilter(products));
        // 가격 필터
        addIfNotEmpty(filterGroups, buildPriceFilter(products));
        // 보관방법 필터
        addIfNotEmpty(filterGroups, buildStorageTypeFilter(storageTypeCounts));

        return new ProductFilterResponse(products.size(), filterGroups);
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

    private FilterGroupDto buildBrandFilter(List<Product> products) {
        Map<String, Long> countByBrand = products.stream()
                .map(Product::getBrand)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), TreeMap::new, Collectors.counting()));

        List<FilterItemDto> items = countByBrand.entrySet().stream()
                .map(entry -> new FilterItemDto(entry.getKey(), entry.getKey(), entry.getValue()))
                .toList();

        return new FilterGroupDto("brand", "브랜드", items);
    }

    private FilterGroupDto buildPriceFilter(List<Product> products) {
        List<Long> prices = products.stream()
                .map(product -> product.getSalePrice() != null ? product.getSalePrice() : product.getPrice())
                .filter(Objects::nonNull)
                .toList();

        List<FilterItemDto> items = Arrays.stream(PriceBand.values())
                .map(band -> new FilterItemDto(band.getLabel(), band.getValue(),
                        prices.stream().filter(band::contains).count()))
                .filter(item -> item.count() > 0)
                .toList();
        return new FilterGroupDto("price", "가격", items);
    }

    private FilterGroupDto buildStorageTypeFilter(List<StorageTypeCount> storageTypeCounts) {
        Map<StorageType, Long> countByType = storageTypeCounts.stream()
                .collect(Collectors.toMap(StorageTypeCount::getStorageType, StorageTypeCount::getCount));

        List<FilterItemDto> items = Arrays.stream(StorageType.values())
                .filter(countByType::containsKey)
                .map(type -> new FilterItemDto(type.getLabel(), type.name(), countByType.get(type)))
                .toList();

        return new FilterGroupDto("storageType", "포장타입", items);
    }

    private void addIfNotEmpty(List<FilterGroupDto> filterGroups, FilterGroupDto filterGroup) {
        if (!filterGroup.items().isEmpty()) {
            filterGroups.add(filterGroup);
        }
    }
}
