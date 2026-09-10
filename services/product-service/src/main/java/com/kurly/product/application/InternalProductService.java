package com.kurly.product.application;

import com.kurly.product.domain.repository.ProductRepository;
import com.kurly.product.infrastructure.entity.Product;
import com.kurly.product.infrastructure.entity.Product.ProductType;
import com.kurly.product.infrastructure.entity.ProductMedia;
import com.kurly.product.infrastructure.entity.ProductMedia.MediaRole;
import com.kurly.product.infrastructure.entity.ProductSpec;
import com.kurly.product.infrastructure.entity.ProductSpec.StorageType;
import com.kurly.product.infrastructure.jpa.ProductInventoryJpaRepository;
import com.kurly.product.infrastructure.jpa.ProductMediaJpaRepository;
import com.kurly.product.infrastructure.jpa.ProductSpecJpaRepository;
import com.kurly.product.presentation.dto.BatchProductSummaryResponse;
import com.kurly.product.presentation.dto.BatchProductSummaryResponse.InventoryInfo;
import com.kurly.product.presentation.dto.BatchProductSummaryResponse.ProductSummaryItem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternalProductService {

    /** 상품별 1회 주문 최대 수량. 상품별 개별 정책이 생기기 전까지 고정값. */
    private static final int MAX_QUANTITY_PER_ORDER = 10;

    /** ProductInventoryRepositoryImpl 의 재고 해시 키/필드와 동일해야 한다. */
    private static final String INVENTORY_KEY_PREFIX = "product:inventory:";
    private static final String FIELD_BASE_QUANTITY = "base_quantity";
    private static final String FIELD_RESERVED_QUANTITY = "reserved_quantity";
    private static final List<String> INVENTORY_FIELDS = List.of(FIELD_BASE_QUANTITY, FIELD_RESERVED_QUANTITY);

    private final ProductRepository productRepository;
    private final ProductSpecJpaRepository productSpecJpaRepository;
    private final ProductInventoryJpaRepository productInventoryJpaRepository;
    private final ProductMediaJpaRepository productMediaRepository;
    private final StringRedisTemplate stringRedisTemplate;

    public BatchProductSummaryResponse getBatchSummary(List<Long> productIds) {
        List<Product> products = productRepository.findAllById(productIds).stream()
                .filter(product -> product.getType() == ProductType.UNIT)
                .toList();
        if (products.isEmpty()) {
            return new BatchProductSummaryResponse(List.of());
        }

        List<Long> parentProductId = products.stream().map(Product::getParentId).toList();

        Map<Long, Product> productById = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<Long> foundIds = List.copyOf(productById.keySet());

        Map<Long, StorageType> storageTypeByProductId = productSpecJpaRepository.findByProductIdIn(foundIds).stream()
                .filter(spec -> spec.getStorageType() != null)
                .collect(Collectors.toMap(spec -> spec.getProduct().getId(), ProductSpec::getStorageType));

        Map<Long, Integer> availableQuantityByProductId = readAvailableQuantitiesFromRedis(foundIds);
        List<Long> missingInRedis = foundIds.stream()
                .filter(id -> !availableQuantityByProductId.containsKey(id))
                .toList();
        if (!missingInRedis.isEmpty()) {
            productInventoryJpaRepository.findByProductIdIn(missingInRedis).forEach(inventory ->
                    availableQuantityByProductId.put(inventory.getProduct().getId(), inventory.getAvailableQuantity()));
        }

        Map<Long, String> thumbnailByProductId = productMediaRepository
                .findByProductIdInAndMediaRole(parentProductId, MediaRole.THUMBNAIL).stream()
                .collect(Collectors.toMap(
                        media -> media.getProduct().getId(),
                        ProductMedia::getMediaUrl,
                        (first, second) -> first));

        List<ProductSummaryItem> items = productIds.stream()
                .distinct()
                .map(productById::get)
                .filter(Objects::nonNull)
                .map(product -> toItem(
                        product,
                        product.getBrand(),
                        storageTypeByProductId.get(product.getId()),
                        availableQuantityByProductId.get(product.getId()),
                        thumbnailByProductId.get(product.getParentId())))
                .toList();

        return new BatchProductSummaryResponse(items);
    }

    private Map<Long, Integer> readAvailableQuantitiesFromRedis(List<Long> productIds) {
        List<String> keys = productIds.stream().map(id -> INVENTORY_KEY_PREFIX + id).toList();

        @SuppressWarnings({"unchecked", "rawtypes"})
        List<Object> pipelined = stringRedisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) {
                HashOperations<String, String, String> hashOps = operations.opsForHash();
                for (String key : keys) {
                    hashOps.multiGet(key, INVENTORY_FIELDS);
                }
                return null;
            }
        });

        Map<Long, Integer> availableByProductId = new HashMap<>();
        for (int i = 0; i < productIds.size(); i++) {
            @SuppressWarnings("unchecked")
            List<String> values = (List<String>) pipelined.get(i);
            if (values == null || values.size() < 2) {
                continue;
            }
            String base = values.get(0);
            String reserved = values.get(1);
            if (base == null || reserved == null) {
                continue;
            }
            availableByProductId.put(productIds.get(i), Integer.parseInt(base) - Integer.parseInt(reserved));
        }
        return availableByProductId;
    }

    private ProductSummaryItem toItem(Product product, String brand, StorageType storageType,
                                      Integer availableQuantityOrNull, String thumbnailUrl) {
        int availableQuantity = availableQuantityOrNull != null ? availableQuantityOrNull : 0;
        return new ProductSummaryItem(
                product.getId(),
                product.getName(),
                product.getSalePrice(),
                thumbnailUrl,
                storageType != null ? storageType.name() : null,
                product.getStatus() != null ? product.getStatus().name() : null,
                brand,
                new InventoryInfo(availableQuantity, availableQuantity <= 0, MAX_QUANTITY_PER_ORDER)
        );
    }
}
