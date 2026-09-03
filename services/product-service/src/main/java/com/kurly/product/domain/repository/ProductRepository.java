package com.kurly.product.domain.repository;

import com.kurly.product.domain.dto.ProductSearchCondition;
import com.kurly.product.infrastructure.entity.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface ProductRepository {
    Optional<Product> findById(Long productId);
    Optional<Product> findByParentId(Long parentId);

    List<Product> findTopLikedProducts(int limit);
    List<Product> findTopDiscountedProducts(int limit);
    List<Product> findTopRepurchaseProducts(int limit);
    List<Product> findProductsByCategoryId(Long categoryId);
    Slice<Product> searchProducts(ProductSearchCondition condition, Pageable pageable);
}
