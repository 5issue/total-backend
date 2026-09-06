package com.kurly.product.application;

import com.kurly.common.exception.BusinessException;
import com.kurly.common.exception.EntityNotFoundException;
import com.kurly.common.exception.GlobalErrorCode;
import com.kurly.product.domain.exception.ProductException;
import com.kurly.product.domain.repository.ProductInventoryRepository;
import com.kurly.product.infrastructure.entity.ProductInventory;
import com.kurly.product.presentation.dto.InventoryAdjustRequest.ReserveItem;
import java.util.ArrayList;
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
    public void hold(Long orderId, List<ReserveItem> items) {
        List<ReserveItem> held = new ArrayList<>();
        try {
            for (ReserveItem item : items) {
                productInventoryRepository.holdInventory(orderId, item.productId(), item.quantity(),
                        item.orderItemId(), RESERVATION_TTL_SECONDS);
                held.add(item);
            }
        } catch (ProductException e) {
            for (ReserveItem item : held) {
                productInventoryRepository.releaseInventory(orderId, item.productId(), item.quantity(),
                        item.orderItemId(), RESERVATION_TTL_SECONDS);
            }
            throw e;
        }
    }

    @Transactional
    public void release(Long orderId, List<ReserveItem> items) {
        for(ReserveItem item : items) {
            productInventoryRepository.releaseInventory(orderId, item.productId(), item.quantity(),
                    item.orderItemId(), RESERVATION_TTL_SECONDS);
        }
    }

    @Transactional
    public void confirm(Long orderId, List<ReserveItem> items) {
//        ProductInventory inventory = productInventoryRepository.findByProductId(orderId)
//                .orElseThrow(() -> new EntityNotFoundException("상품 재고를 찾을 수 없습니다. productId=" + orderId));
//
//        if (inventory.getAvailableQuantity() < quantity) {
//            throw new BusinessException(GlobalErrorCode.CONFLICT, "재고가 부족합니다.");
//        }
//        inventory.hold(quantity);
    }


}
