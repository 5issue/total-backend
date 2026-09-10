package com.kurly.product.application;

import com.kurly.product.application.support.FilterLayoutProvider;
import com.kurly.product.domain.enums.PriceBand;
import com.kurly.product.domain.repository.ProductRepository;
import com.kurly.product.infrastructure.entity.Product;
import com.kurly.product.infrastructure.entity.ProductSpec.StorageType;
import com.kurly.product.infrastructure.jpa.ProductSpecJpaRepository;
import com.kurly.product.infrastructure.jpa.ProductSpecJpaRepository.StorageTypeCount;
import com.kurly.product.presentation.dto.ProductFilterResponse;
import com.kurly.product.presentation.dto.ProductFilterResponse.FilterGroupDto;
import com.kurly.product.presentation.dto.ProductFilterResponse.FilterItemDto;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductFilterService {

    private final ProductRepository productRepository;
    private final ProductSpecJpaRepository productSpecRepository;
    private final FilterLayoutProvider filterLayoutProvider;


    @Cacheable(value = "productFilters", key = "#categoryId")
    public ProductFilterResponse buildFilterByCategory(Long categoryId) {
        List<Product> products = productRepository.findProductsByCategoryId(categoryId);
        return buildFilterResponse(products);
    }

    public ProductFilterResponse buildFilterByKeyword(String keyword) {
        List<Product> products = productRepository.findProductsByKeyword(keyword.trim());
        // 카테고리 필터링 옵션 추가 고려
        return buildFilterResponse(products);
    }

    private ProductFilterResponse buildFilterResponse(List<Product> products) {
        List<Long> groupIds = products.stream().map(Product::getId).toList();
        List<StorageTypeCount> storageTypeCounts = groupIds.isEmpty()
                ? List.of() : productSpecRepository.countStorageTypesByGroupIdIn(groupIds);

        List<FilterGroupDto> filterGroups = new ArrayList<>();
        filterGroups.add(new FilterGroupDto("sort", "정렬", filterLayoutProvider.getSortFilters()));
        // 판매업체(브랜드) 필터
        addIfNotEmpty(filterGroups, buildBrandFilter(products));
        // 가격 필터
        addIfNotEmpty(filterGroups, buildPriceFilter(products));
        // 보관방법 필터
        addIfNotEmpty(filterGroups, buildStorageTypeFilter(storageTypeCounts));

        return new ProductFilterResponse(products.size(), filterGroups);
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
