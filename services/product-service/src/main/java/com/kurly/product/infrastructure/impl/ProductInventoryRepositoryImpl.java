package com.kurly.product.infrastructure.impl;

import com.kurly.common.exception.EntityNotFoundException;
import com.kurly.product.domain.exception.ProductErrorCode;
import com.kurly.product.domain.exception.ProductException;
import com.kurly.product.domain.repository.ProductInventoryRepository;
import com.kurly.product.infrastructure.entity.ProductInventory;
import com.kurly.product.infrastructure.jpa.ProductInventoryJpaRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ProductInventoryRepositoryImpl implements ProductInventoryRepository {

    private final StringRedisTemplate redisTemplate;
    private RedisScript<Long> holdScript;
    private RedisScript<Long> releaseScript;
    private final ProductInventoryJpaRepository productInventoryJpaRepository;

    @PostConstruct
    public void init() {
        this.holdScript = RedisScript.of(new ClassPathResource("lua/stock_hold.lua"), Long.class);
        this.releaseScript = RedisScript.of(new ClassPathResource("lua/stock_release.lua"), Long.class);
    }

    @Override
    public Optional<ProductInventory> findByProductId(Long productId) {
        return productInventoryJpaRepository.findByProductId(productId);
    }

    @Override
    public void holdInventory(Long orderId, Long productId, int quantity, Long orderItemId, Long ttlSeconds) {
        List<String> keys = List.of(getKey(productId), getHoldIdempotencyKey(orderId, orderItemId));

        Long result = executeScript(holdScript, keys, quantity, ttlSeconds);
        if (result != null && result == -1L) {
            syncInventoryToRedis(productId);
            result = executeScript(holdScript, keys, quantity, ttlSeconds);
        }

        if (result == null || result == -1L) {
            // 스크립트가 nil 을 반환했거나, 동기화 후에도 재고 해시가 없는 경우 → 인프라 이상
            throw new ProductException(ProductErrorCode.INVENTORY_UNAVAILABLE);
        }
        if (result == 0L) {
            throw new ProductException(ProductErrorCode.OUT_OF_STOCK);
        }
    }

    @Override
    public void releaseInventory(Long orderId, Long productId, int quantity, Long orderItemId, Long ttlSeconds) {
        List<String> keys = List.of(getKey(productId), getReleaseIdempotencyKey(orderId, orderItemId));

        Long result = executeScript(releaseScript, keys, quantity, ttlSeconds);
        if (result != null && result == -1L) {
            log.warn("선점 취소 요청이 들어왔으나 Redis에 해당 상품 재고 키가 존재하지 않습니다 (만료 또는 휘발). orderItemId: {}", orderItemId);
        }
    }

    @Override
    public void syncInventoryToRedis(Long productId) {
        ProductInventory inventory = productInventoryJpaRepository.findByProductId(productId)
                .orElseThrow(() -> new EntityNotFoundException("상품 재고를 찾을 수 없습니다. productId=" + productId));

        String key = getKey(productId);
        redisTemplate.opsForHash().put(key, "base_quantity", String.valueOf(inventory.getBaseQuantity()));
        redisTemplate.opsForHash().put(key, "reserved_quantity", String.valueOf(inventory.getReservedQuantity()));
    }

    private Long executeScript(RedisScript<Long> script, List<String> keys, int quantity, Long ttlSeconds) {
        return redisTemplate.execute(script, keys, String.valueOf(quantity), String.valueOf(ttlSeconds));
    }
    private String getKey(Long productId) {
        return "product:inventory:" + productId;
    }
    private String getHoldIdempotencyKey(Long orderId, Long orderItemId) {
        return "idempotency:hold:" + orderId + ":" + orderItemId;
    }
    private String getReleaseIdempotencyKey(Long orderId, Long orderItemId) {
        return "idempotency:release:" + orderId + ":" + orderItemId;
    }

}
