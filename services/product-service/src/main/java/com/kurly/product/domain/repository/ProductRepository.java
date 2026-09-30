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

    List<Product> findAllById(List<Long> productIds);

    /** AI 서버 product_id 로 조회(HIDDEN 제외). GROUP 과 UNIT 이 같은 값을 공유할 수 있어 여러 건이 나올 수 있다. */
    List<Product> findByAiProductIds(List<Long> aiProductIds);

    List<Product> findTopLikedProducts(int limit);
    List<Product> findTopDiscountedProducts(int limit);
    List<Product> findTopRepurchaseProducts(int limit);
    List<Product> findProductsByCategoryId(Long categoryId);
    List<Product> findProductsByKeyword(String keyword);
    Slice<Product> searchProducts(ProductSearchCondition condition, Pageable pageable);
}
