package com.kurly.product.application;

import com.kurly.common.exception.EntityNotFoundException;
import com.kurly.common.exception.InvalidValueException;
import com.kurly.product.domain.dto.ReserveItem;
import com.kurly.product.domain.enums.ConsumedEventType;
import com.kurly.product.domain.exception.ProductErrorCode;
import com.kurly.product.domain.exception.ProductException;
import com.kurly.product.domain.repository.ProductInventoryRepository;
import com.kurly.product.infrastructure.entity.ProductInventory;
import com.kurly.product.infrastructure.jpa.ProductConsumedEventJpaRepository;
import com.kurly.product.infrastructure.messaging.dto.ProductInventoryConfirmedEvent;
import com.kurly.product.infrastructure.messaging.dto.ProductInventoryConfirmedEvent.FailedItemInfo;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductInventoryService {

    private static final long RESERVATION_TTL_SECONDS = 20 * 60;

    private final ProductInventoryRepository productInventoryRepository;
    private final OutboxService outboxService;
    private final ProductConsumedEventJpaRepository productConsumedEventJpaRepository;

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
    public void confirm(String reservationToken, Long orderId, List<ReserveItem> items) {
        // Redis 의 예약 토큰 멱등성은 Redis 상태만 지켜 준다. DB 선점 수량은 여기서 따로 한 번만 반영한다.
        if (!markFirstRequest(ConsumedEventType.INVENTORY_CONFIRM_REQUESTED, reservationToken)) {
            log.info("이미 처리된 재고 확정 요청, 스킵: reservationToken={}, orderId={}", reservationToken, orderId);
            return;
        }

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

        productInventoryRepository.confirmInventory(reservationToken, RESERVATION_TTL_SECONDS);
    }

    @Transactional
    public void restore(Long orderId, List<ReserveItem> items) {
        // 복구 요청은 예약 토큰이 없어, 주문 단위로 한 번만 반영한다(중복 복구는 다른 주문의 선점까지 풀어 버린다).
        if (!markFirstRequest(ConsumedEventType.INVENTORY_RESTORE_REQUESTED, String.valueOf(orderId))) {
            log.info("이미 처리된 재고 복구 요청, 스킵: orderId={}", orderId);
            return;
        }

        for (ReserveItem item : items) {
            ProductInventory inventory = productInventoryRepository.findByProductId(item.productId()
            ).orElseThrow(() -> new EntityNotFoundException("상품 재고를 찾을 수 없습니다. productId=" + item.productId()));

            inventory.restore(item.quantity());
        }

        outboxService.recordRestored(orderId);

        productInventoryRepository.restoreInventory(null, items.stream().map(ReserveItem::productId).toList(), items.stream().map(ReserveItem::quantity).toList());
    }

    @Transactional
    public void increaseStock(Long productId, int quantity) {
        ProductInventory inventory = productInventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new EntityNotFoundException("상품 재고를 찾을 수 없습니다. productId=" + productId));

        inventory.increaseBaseQuantity(quantity);
        inventory.getProduct().restock();

        productInventoryRepository.increaseInventory(productId, quantity);
    }

    @Transactional
    public void finalizeOutbound(List<ReserveItem> items) {
        for (ReserveItem item : items) {
            ProductInventory inventory = productInventoryRepository.findByProductId(item.productId())
                    .orElseThrow(() -> new EntityNotFoundException("상품 재고를 찾을 수 없습니다. productId=" + item.productId()));

            inventory.finalizeOutbound(item.quantity());
            productInventoryRepository.finalizeOutboundInventory(item.productId(), item.quantity());
        }
    }

    /**
     * 요청 키를 인박스에 원자적으로 적재해 처음 보는 요청인지 확인한다. 이미 처리한 요청이면 false.
     * 업무 변경과 같은 트랜잭션이라 요청이 실패해 롤백되면 마커도 함께 사라져 재시도할 수 있다. 같은 요청이
     * 동시에 들어와도 두 번째는 첫 번째 트랜잭션이 끝나기를 기다린 뒤 예외 없이 false 를 받는다(재고 행을 건드리기 전).
     */
    private boolean markFirstRequest(ConsumedEventType type, String requestKey) {
        String eventId = UUID.nameUUIDFromBytes((type + ":" + requestKey).getBytes(StandardCharsets.UTF_8)).toString();
        return productConsumedEventJpaRepository.insertIfAbsent(eventId, type.name()) == 1;
    }
}
