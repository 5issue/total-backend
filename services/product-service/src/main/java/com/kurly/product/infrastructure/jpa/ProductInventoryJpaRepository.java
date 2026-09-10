package com.kurly.product.infrastructure.jpa;

import com.kurly.product.infrastructure.entity.ProductInventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductInventoryJpaRepository extends JpaRepository<ProductInventory, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProductInventory> findByProductId(Long productId);

    // 조회 전용(락 없음) 배치 조회.
    List<ProductInventory> findByProductIdIn(Collection<Long> productIds);
}
