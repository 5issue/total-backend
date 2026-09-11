package com.kurly.product.domain.repository;

import com.kurly.product.infrastructure.entity.ProductInventory;
import java.util.List;
import java.util.Optional;

public interface ProductInventoryRepository {

    Optional<ProductInventory> findByProductId(Long productId);

    void holdInventory(String eventId, List<Long> productIds, List<Integer> quantities, Long ttlSeconds);

    void releaseInventory(String eventId, List<Long> productIds, List<Integer> quantities, Long ttlSeconds);

    void syncInventoryToRedis(Long productId);
//    int getAvailableInventory(Long productId);
}
