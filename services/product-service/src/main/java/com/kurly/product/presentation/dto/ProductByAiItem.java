package com.kurly.product.presentation.dto;

import com.kurly.product.infrastructure.entity.Product;
import com.kurly.product.infrastructure.entity.Product.ProductStatus;
import com.kurly.product.infrastructure.entity.Product.ProductType;

/**
 * AI 서버 product_id 로 조회한 BE 상품 한 건. GROUP/UNIT 모두 같은 형태다.
 * aiProductId 는 GROUP 과 UNIT 이 같은 값을 공유할 수 있어(BE 가 합성한 UNIT) 구분은 type 으로 한다.
 */
public record ProductByAiItem(
        Long aiProductId,
        Long id,
        String skuCode,
        ProductType type,
        Long parentId,
        String name,
        String brand,
        Long price,
        Long salePrice,
        Integer discountRate,
        ProductStatus status,
        String thumbnailUrl
) {
    public static ProductByAiItem of(Product product, String thumbnailUrl) {
        return new ProductByAiItem(
                product.getAiProductId(),
                product.getId(),
                product.getSkuCode(),
                product.getType(),
                product.getParentId(),
                product.getName(),
                product.getBrand(),
                product.getPrice(),
                product.getSalePrice(),
                product.getDiscountRate(),
                product.getStatus(),
                thumbnailUrl
        );
    }
}
