package com.kurly.product.domain.repository;

import com.kurly.product.infrastructure.entity.ProductInventory;
import java.util.List;
import java.util.Optional;

public interface ProductInventoryRepository {

    Optional<ProductInventory> findByProductId(Long productId);

    void holdInventory(String reservationToken, List<Long> productIds, List<Integer> quantities);

    void releaseInventory(String reservationToken, Long ttlSeconds);

    void confirmInventory(String reservationToken, Long ttlSeconds);

    void restoreInventory(String reservationToken, List<Long> productIds, List<Integer> quantities);

    void syncInventoryToRedis(Long productId);
}
