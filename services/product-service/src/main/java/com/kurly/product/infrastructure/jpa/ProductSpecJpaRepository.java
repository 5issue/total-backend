package com.kurly.product.infrastructure.jpa;

import com.kurly.product.infrastructure.entity.ProductSpec;
import com.kurly.product.infrastructure.entity.ProductSpec.StorageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductSpecJpaRepository extends JpaRepository<ProductSpec, Long> {

    Optional<ProductSpec> findByProductId(Long productId);

    List<ProductSpec> findByProductIdIn(Collection<Long> productIds);

    /**
     * 주어진 GROUP 상품들의 자식 UNIT 이 가진 storage_type 별로,
     * 해당 보관타입을 하나 이상 가진 GROUP 상품의 수를 센다(필터 노출용).
     * product_spec 은 UNIT 에만 있으므로 ps.product.parentId 가 GROUP id 다.
     */
    @Query("""
            select ps.storageType as storageType, count(distinct ps.product.parentId) as count
            from ProductSpec ps
            where ps.product.parentId in :groupIds
              and ps.storageType is not null
            group by ps.storageType
            """)
    List<StorageTypeCount> countStorageTypesByGroupIdIn(@Param("groupIds") Collection<Long> groupIds);

    interface StorageTypeCount {
        StorageType getStorageType();

        long getCount();
    }
}
