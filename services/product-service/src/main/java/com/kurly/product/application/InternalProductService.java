package com.kurly.product.application;

import com.kurly.product.domain.repository.ProductRepository;
import com.kurly.product.infrastructure.entity.Product;
import com.kurly.product.infrastructure.entity.Product.ProductType;
import com.kurly.product.infrastructure.entity.ProductInventory;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternalProductService {

    /** 상품별 1회 주문 최대 수량. 상품별 개별 정책이 생기기 전까지 고정값. */
    private static final int MAX_QUANTITY_PER_ORDER = 10;

    private final ProductRepository productRepository;
    private final ProductSpecJpaRepository productSpecJpaRepository;
    private final ProductInventoryJpaRepository productInventoryJpaRepository;
    private final ProductMediaJpaRepository productMediaRepository;

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

        Map<Long, ProductInventory> inventoryByProductId = productInventoryJpaRepository.findByProductIdIn(foundIds).stream()
                .collect(Collectors.toMap(inv -> inv.getProduct().getId(), Function.identity()));



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
                        inventoryByProductId.get(product.getId()),
                        thumbnailByProductId.get(product.getParentId())))
                .toList();

        return new BatchProductSummaryResponse(items);
    }

    private ProductSummaryItem toItem(Product product, String brand, StorageType storageType,
                                      ProductInventory inventory, String thumbnailUrl) {
        int availableQuantity = inventory != null ? inventory.getAvailableQuantity() : 0;
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
