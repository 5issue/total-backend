package com.kurly.product.application;

import com.kurly.common.exception.EntityNotFoundException;
import com.kurly.common.exception.InvalidValueException;
import com.kurly.product.domain.dto.ReserveItem;
import com.kurly.product.domain.exception.ProductErrorCode;
import com.kurly.product.domain.exception.ProductException;
import com.kurly.product.domain.repository.ProductInventoryRepository;
import com.kurly.product.infrastructure.entity.ProductInventory;
import com.kurly.product.infrastructure.messaging.dto.ProductInventoryConfirmedEvent;
import com.kurly.product.infrastructure.messaging.dto.ProductInventoryConfirmedEvent.FailedItemInfo;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductInventoryService {

    private static final long RESERVATION_TTL_SECONDS = 20 * 60;

    private final ProductInventoryRepository productInventoryRepository;
    private final OutboxService outboxService;

    @Transactional
    public void hold(String reservationToken, List<ReserveItem> items) {
        List<Long> productIds = items.stream().map(ReserveItem::productId).toList();
        List<Integer> quantities = items.stream().map(ReserveItem::quantity).toList();

        productInventoryRepository.holdInventory(reservationToken, productIds, quantities);
    }

    @Transactional
    public void release(String reservationToken) {
        productInventoryRepository.releaseInventory(reservationToken, RESERVATION_TTL_SECONDS);
    }

    @Transactional
    public void confirm(Long orderId, List<ReserveItem> items) {
        long distinctProductIdCount = items.stream().map(ReserveItem::productId).distinct().count();
        if (distinctProductIdCount != items.size()) {
            throw new InvalidValueException("items에 같은 productId가 중복될 수 없습니다.");
        }

        Map<Long, ProductInventory> inventories = new LinkedHashMap<>();
        for (ReserveItem item : items) {
            inventories.computeIfAbsent(item.productId(), productId -> productInventoryRepository.findByProductId(productId)
                    .orElseThrow(() -> new EntityNotFoundException("상품 재고를 찾을 수 없습니다. productId=" + productId)));
        }

        List<FailedItemInfo> failedItems = items.stream()
                .filter(item -> inventories.get(item.productId()).getAvailableQuantity() < item.quantity())
                .map(item -> new FailedItemInfo(item.productId(), item.quantity()))
                .toList();

        if (!failedItems.isEmpty()) {
            outboxService.recordConfirmFailed(orderId,
                    ProductInventoryConfirmedEvent.Status.INSUFFICIENT_STOCK, failedItems);
            throw new ProductException(ProductErrorCode.OUT_OF_STOCK);
        }

        for (ReserveItem item : items) {
            inventories.get(item.productId()).hold(item.quantity());
        }

        outboxService.recordConfirmed(orderId);
    }

    @Transactional
    public void restore(Long orderId, List<ReserveItem> items) {
        for (ReserveItem item : items) {
            ProductInventory inventory = productInventoryRepository.findByProductId(item.productId()
            ).orElseThrow(() -> new EntityNotFoundException("상품 재고를 찾을 수 없습니다. productId=" + item.productId()));

            inventory.restore(item.quantity());
        }

        outboxService.recordRestored(orderId);
    }
}
