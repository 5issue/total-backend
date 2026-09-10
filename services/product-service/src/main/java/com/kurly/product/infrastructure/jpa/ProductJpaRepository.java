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
     * 카테고리(하위 포함) 내 상품 목록 조회.
     * <p>
     * 카테고리 필터가 필요하므로 product_category 를 조인하고(상품이 여러 카테고리에
     * 매핑될 수 있어 {@code distinct}), 나머지 필터 파라미터는 nullable 이다.
     * 가격은 {@code minPrice <= salePrice < maxPrice} 반개구간으로 비교한다
     * ({@link com.kurly.product.domain.enums.PriceBand} 의 경계 규칙과 동일).
     * product_spec 은 UNIT 상품에만 매핑되므로, storageType 필터는 GROUP 상품(p)의
     * 자식 UNIT 중 해당 보관타입 spec 을 가진 것이 하나라도 있는지를 EXISTS 로 본다.
     * 정렬은 {@link Pageable} 의 {@code Sort} 로, 결과는 무한 스크롤용 {@link Slice} 로 반환한다.
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
    Slice<Product> searchInCategories(@Param("categoryIds") List<Long> categoryIds,
                                      @Param("type") ProductType type,
                                      @Param("status") ProductStatus status,
                                      @Param("brand") String brand,
                                      @Param("minPrice") Long minPrice,
                                      @Param("maxPrice") Long maxPrice,
                                      @Param("storageType") StorageType storageType,
                                      @Param("keyword") String keyword,
                                      Pageable pageable);

    /**
     * 카테고리 제한 없는(홈 검색용) 상품 목록 조회.
     * product_category 조인이 없어 {@code distinct} 도 불필요하다. 그 외 조건은
     * {@link #searchInCategories} 와 동일.
     */
    @Query("""
            select p from Product p
            where p.type = :type
              and p.status = :status
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
    Slice<Product> search(@Param("type") ProductType type,
                          @Param("status") ProductStatus status,
                          @Param("brand") String brand,
                          @Param("minPrice") Long minPrice,
                          @Param("maxPrice") Long maxPrice,
                          @Param("storageType") StorageType storageType,
                          @Param("keyword") String keyword,
                          Pageable pageable);

    /**
     * 키워드로 매칭되는 판매중 GROUP 상품 전체(페이징 없음). 필터 옵션 집계용.
     */
    @Query("""
            select p from Product p
            where p.type = :type
              and p.status = :status
              and lower(p.name) like lower(concat('%', cast(:keyword as string), '%'))
            """)
    List<Product> findByKeyword(@Param("type") ProductType type,
                                @Param("status") ProductStatus status,
                                @Param("keyword") String keyword);

}
