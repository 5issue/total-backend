package com.kurly.product.infrastructure.jpa;

import com.kurly.product.infrastructure.entity.ProductInventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductInventoryJpaRepository extends JpaRepository<ProductInventory, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProductInventory> findByProductId(Long productId);
}
