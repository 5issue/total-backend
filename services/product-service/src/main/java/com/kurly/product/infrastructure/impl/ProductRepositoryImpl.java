package com.kurly.product.infrastructure.impl;

import com.kurly.product.domain.repository.ProductRepository;
import com.kurly.product.domain.dto.ProductSearchCondition;
import com.kurly.product.infrastructure.entity.Product;
import com.kurly.product.infrastructure.entity.Product.ProductStatus;
import com.kurly.product.infrastructure.entity.Product.ProductType;
import com.kurly.product.infrastructure.jpa.CategoryJpaRepository;
import com.kurly.product.infrastructure.jpa.ProductJpaRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import java.util.Collections;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;
    private final CategoryJpaRepository categoryJpaRepository;

    @Override
    public Optional<Product> findById(Long productId) {
        return productJpaRepository.findById(productId);
    }

    @Override
    public Optional<Product> findByParentId(Long parentId) {
        return productJpaRepository.findByParentId(parentId);
    }

    @Override
    public List<Product> findTopLikedProducts(int limit) {
        return productJpaRepository.findByStatusAndTypeOrderByLikeCountDesc(ProductStatus.SALE, ProductType.GROUP, PageRequest.of(0, limit));
    }

    @Override
    public List<Product> findTopDiscountedProducts(int limit) {
        return productJpaRepository.findByStatusAndTypeAndDiscountRateGreaterThanOrderByDiscountRateDesc(ProductStatus.SALE, ProductType.GROUP, 0, PageRequest.of(0, limit));
    }

    @Override
    public List<Product> findTopRepurchaseProducts(int limit) {
        return productJpaRepository.findByStatusAndTypeOrderByTotalSalesCountDesc(ProductStatus.SALE, ProductType.GROUP, PageRequest.of(0, limit));
    }

    @Override
    public List<Product> findProductsByCategoryId(Long categoryId) {
        List<Long> categoryIds = categoryJpaRepository.findAllSubCategoryIds(categoryId);
        return productJpaRepository.findByCategoryIdIn(categoryIds);
    }

    @Override
    public Slice<Product> searchProducts(ProductSearchCondition condition, Pageable pageable) {
        List<Long> categoryIds = categoryJpaRepository.findAllSubCategoryIds(condition.categoryId());
        if (categoryIds.isEmpty()) {
            return new SliceImpl<>(Collections.emptyList(), pageable, false);
        }

        return productJpaRepository.search(
                categoryIds,
                ProductType.GROUP,
                ProductStatus.SALE,
                condition.brand(),
                condition.minPrice(),
                condition.maxPrice(),
                condition.storageType(),
                condition.normalizedKeyword(),
                pageable
        );
    }
}
