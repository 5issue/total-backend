package com.kurly.product.application;

import com.kurly.common.exception.BusinessException;
import com.kurly.common.exception.EntityNotFoundException;
import com.kurly.common.exception.GlobalErrorCode;
import com.kurly.product.domain.dto.ReserveItem;
import com.kurly.product.domain.repository.ProductInventoryRepository;
import com.kurly.product.infrastructure.entity.ProductInventory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductInventoryService {

    private final ProductInventoryRepository productInventoryRepository;
    private static final long RESERVATION_TTL_SECONDS = 20 * 60;

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
    public void confirm(String eventId, List<ReserveItem> items) {
        for (ReserveItem item : items) {
            ProductInventory inventory = productInventoryRepository.findByProductId(item.productId()
            ).orElseThrow(() -> new EntityNotFoundException("상품 재고를 찾을 수 없습니다. productId=" + item.productId()));

            if (inventory.getAvailableQuantity() < item.quantity()) {
                throw new BusinessException(GlobalErrorCode.CONFLICT, "재고가 부족합니다.");
            }
            inventory.hold(item.quantity());
        }
    }

    @Transactional
    public void restore(String eventId, List<ReserveItem> items) {
        for (ReserveItem item : items) {
            ProductInventory inventory = productInventoryRepository.findByProductId(item.productId()
            ).orElseThrow(() -> new EntityNotFoundException("상품 재고를 찾을 수 없습니다. productId=" + item.productId()));

            inventory.restore(item.quantity());
        }
    }
}
