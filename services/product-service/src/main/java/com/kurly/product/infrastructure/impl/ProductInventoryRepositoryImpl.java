package com.kurly.product.infrastructure.impl;

import com.kurly.common.exception.EntityNotFoundException;
import com.kurly.product.domain.exception.ProductErrorCode;
import com.kurly.product.domain.exception.ProductException;
import com.kurly.product.domain.repository.ProductInventoryRepository;
import com.kurly.product.infrastructure.entity.ProductInventory;
import com.kurly.product.infrastructure.jpa.ProductInventoryJpaRepository;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private RedisScript<Long> confirmScript;
    private RedisScript<Long> releaseScript;
    private RedisScript<Long> restoreScript;
    private final ProductInventoryJpaRepository productInventoryJpaRepository;

    @PostConstruct
    public void init() {
        this.holdScript = RedisScript.of(new ClassPathResource("lua/stock_hold.lua"), Long.class);
        this.confirmScript = RedisScript.of(new ClassPathResource("lua/stock_confirm.lua"), Long.class);
        this.releaseScript = RedisScript.of(new ClassPathResource("lua/stock_release.lua"), Long.class);
        this.restoreScript = RedisScript.of(new ClassPathResource("lua/stock_restore.lua"), Long.class);
    }

    @Override
    public Optional<ProductInventory> findByProductId(Long productId) {
        return productInventoryJpaRepository.findByProductId(productId);
    }

    @Override
    public void holdInventory(String reservationToken, List<Long> productIds, List<Integer> quantities) {
        String reservationTokenKey = getReservationKey(reservationToken);
        List<String> keys = new ArrayList<>();
        List<String> args = new ArrayList<>();
        keys.add(reservationTokenKey);

        for (int i = 0; i < productIds.size(); i++) {
            keys.add(getKey(productIds.get(i)));
            args.add(String.valueOf(productIds.get(i)));
            args.add(String.valueOf(quantities.get(i)));
        }

        Long result = executeScript(holdScript, keys, args);

        if (result != null && result == -1L) {
            for (int i = 0; i < productIds.size(); i++) {
                Long productId = productIds.get(i);
                String key = getKey(productId);
                Boolean hasKey = redisTemplate.hasKey(key);

                if (hasKey == null || !hasKey) {
                    syncInventoryToRedis(productId);
                }
            }
            result = executeScript(holdScript, keys, args);
        }

        if (result == null || result == -1L) {
            throw new ProductException(ProductErrorCode.INVENTORY_UNAVAILABLE);
        }
        if (result == 0L) {
            throw new ProductException(ProductErrorCode.OUT_OF_STOCK);
        }
    }

    @Override
    public void releaseInventory(String reservationToken, Long ttlSeconds) {
        String reservationTokenKey = getReservationKey(reservationToken);

        List<String> keys = List.of(reservationTokenKey);
        List<String> args = List.of(String.valueOf(ttlSeconds));

        Long result = executeScript(releaseScript, keys, args);

        if (result != null && result == -2L) {
            log.warn("이미 확정된 예약에 대한 해제 요청을 무시한다: reservationToken={}", reservationToken);
        }
    }

    @Override
    public void confirmInventory(String reservationToken, Long ttlSeconds) {
        String reservationTokenKey = getReservationKey(reservationToken);

        List<String> keys = List.of(reservationTokenKey);
        List<String> args = List.of(String.valueOf(ttlSeconds));

        Long result = executeScript(confirmScript, keys, args);

        if (result == null || result == -1L) {
            throw new ProductException(ProductErrorCode.INVALID_RESERVATION_STATE);
        }
    }

    @Override
    public void restoreInventory(String reservationToken, List<Long> productIds, List<Integer> quantities) {
        List<String> keys = new ArrayList<>();
        List<String> args = new ArrayList<>();

        for (int i = 0; i < productIds.size(); i++) {
            keys.add(getKey(productIds.get(i)));
            args.add(String.valueOf(quantities.get(i)));
        }

        executeScript(restoreScript, keys, args);
    }


    @Override
    public void syncInventoryToRedis(Long productId) {
        ProductInventory inventory = productInventoryJpaRepository.findByProductId(productId)
                .orElseThrow(() -> new EntityNotFoundException("상품 재고를 찾을 수 없습니다. productId=" + productId));

        String key = getKey(productId);
        redisTemplate.opsForHash().putAll(key, Map.of(
                "base_quantity", String.valueOf(inventory.getBaseQuantity()),
                "reserved_quantity", String.valueOf(inventory.getReservedQuantity())));
    }

    private Long executeScript(RedisScript<Long> script, List<String> keys, List<String> args) {
        return redisTemplate.execute(script, keys, args.toArray());
    }
    private String getKey(Long productId) {
        return "product:inventory:" + productId;
    }
    private String getReservationKey(String reservationToken) {
        return "reservation:" + reservationToken;
    }

}
