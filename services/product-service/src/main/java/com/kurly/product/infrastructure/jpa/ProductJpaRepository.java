package com.kurly.product.infrastructure.jpa;

import com.kurly.product.infrastructure.entity.Product;
import com.kurly.product.infrastructure.entity.Product.ProductStatus;
import com.kurly.product.infrastructure.entity.Product.ProductType;
import com.kurly.product.infrastructure.entity.ProductSpec.StorageType;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByParentId(Long parentId);

    @Query(value = """
            select distinct p from Product p
            join ProductCategory pc on pc.product = p
            where pc.category.id in :categoryIds
            """)
    List<Product> findByCategoryIdIn(@Param("categoryIds") List<Long> categoryIds);

    List<Product> findByStatusAndTypeOrderByLikeCountDesc(
            ProductStatus status,
            ProductType type,
            Pageable pageable
    );

    // 2. 할인율 높은 순 상위 N개 조회 (TOP_DISCOUNTED_PRODUCTS)
    List<Product> findByStatusAndTypeAndDiscountRateGreaterThanOrderByDiscountRateDesc(
            ProductStatus status,
            ProductType type,
            Integer discountRate,
            Pageable pageable
    );

    // 3. 누적 판매량 순 상위 N개 조회 (TOP_REPURCHASE_PRODUCTS)
    List<Product> findByStatusAndTypeOrderByTotalSalesCountDesc(
            ProductStatus status,
            ProductType type,
            Pageable pageable
    );

    /**
     * 상품 목록 조회.
     * <p>
     * 모든 필터 파라미터는 nullable 이며, null 이면 해당 조건을 건너뛴다.
     * 가격은 {@code minPrice <= salePrice < maxPrice} 반개구간으로 비교한다
     * ({@link com.kurly.product.domain.enums.PriceBand} 의 경계 규칙과 동일).
     * product_spec 은 UNIT 상품에만 매핑되므로, storageType 필터는 GROUP 상품(p)의
     * 자식 UNIT 중 해당 보관타입 spec 을 가진 것이 하나라도 있는지를 EXISTS 로 본다.
     * 정렬은 {@link Pageable} 의 {@code Sort} 로 전달한다.
     * <p>
     * 앱 목록(무한 스크롤)용이라 {@link Slice} 로 반환한다. 페이지마다 count 쿼리를
     * 실행하지 않고 {@code size + 1} 건을 조회해 다음 페이지 존재 여부만 판단한다.
     * 전체 개수가 필요하면 {@code getFilters} 응답의 totalCount 를 사용한다.
     */
    @Query("""
            select distinct p from Product p
            join ProductCategory pc on pc.product = p
            where p.type = :type
              and p.status = :status
              and pc.category.id in :categoryIds
              and (:brand is null or p.brand = :brand)
              and (:minPrice is null or p.salePrice >= :minPrice)
              and (:maxPrice is null or p.salePrice < :maxPrice)
              and (:storageType is null or exists (
                    select 1 from ProductSpec ps
                    where ps.product.parentId = p.id
                      and ps.storageType = :storageType
              ))
              and (:keyword is null or lower(p.name) like lower(concat('%', cast(:keyword as string), '%')))
            """)
    Slice<Product> search(@Param("categoryIds") List<Long> categoryIds,
                         @Param("type") ProductType type,
                         @Param("status") ProductStatus status,
                         @Param("brand") String brand,
                         @Param("minPrice") Long minPrice,
                         @Param("maxPrice") Long maxPrice,
                         @Param("storageType") StorageType storageType,
                         @Param("keyword") String keyword,
                         Pageable pageable);

    @Query("select distinct p.brand from Product p where p.brand is not null order by p.brand")
    List<String> findDistinctBrands();

    @Query("select min(p.salePrice) as minPrice, max(p.salePrice) as maxPrice from Product p")
    PriceRangeView findPriceRange();

    interface PriceRangeView {
        Long getMinPrice();

        Long getMaxPrice();
    }
}
