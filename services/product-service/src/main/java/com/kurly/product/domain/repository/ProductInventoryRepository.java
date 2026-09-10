package com.kurly.product.domain.repository;

import com.kurly.product.infrastructure.entity.ProductInventory;
import java.util.Optional;

public interface ProductInventoryRepository {

    Optional<ProductInventory> findByProductId(Long productId);

    void holdInventory(Long orderId, Long productId, int quantity, Long orderItemId, Long ttlSeconds);

    void releaseInventory(Long orderId, Long productId, int quantity, Long orderItemId, Long ttlSeconds);

    void syncInventoryToRedis(Long productId);
//    int getAvailableInventory(Long productId);
}
