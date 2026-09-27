package com.kurly.product.application;

import com.kurly.product.domain.dto.ReserveItem;
import com.kurly.product.domain.enums.ConsumedEventType;
import com.kurly.product.infrastructure.entity.ProductConsumedEvent;
import com.kurly.product.infrastructure.jpa.ProductConsumedEventJpaRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * WMS가 발행하는 입고/출고 완료 이벤트의 소비 유스케이스.
 *
 * <p>같은 이벤트가 재전달되어도 재고가 이중 반영되지 않도록, 실제 처리 전 인박스 테이블에
 * event_id를 먼저 적재한다(체크-후-삽입). 마커 적재와 재고 반영은 같은 트랜잭션으로 묶여
 * 원자적으로 커밋/롤백된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WmsEventService {

    private final ProductConsumedEventJpaRepository productConsumedEventJpaRepository;
    private final ProductInventoryService productInventoryService;

    @Transactional
    public void handleInboundCompleted(UUID eventId, Long productId, int quantity) {
        String eventIdValue = eventId.toString();
        if (productConsumedEventJpaRepository.existsByEventId(eventIdValue)) {
            log.info("이미 처리된 입고 완료 이벤트, 스킵: eventId={}, productId={}", eventIdValue, productId);
            return;
        }

        productConsumedEventJpaRepository.save(ProductConsumedEvent.builder()
                .eventId(eventIdValue)
                .eventType(ConsumedEventType.INBOUND_COMPLETED)
                .build());

        productInventoryService.increaseStock(productId, quantity);
    }

    @Transactional
    public void handleOutboundCompleted(UUID eventId, Long orderId, List<ReserveItem> items) {
        String eventIdValue = eventId.toString();
        if (productConsumedEventJpaRepository.existsByEventId(eventIdValue)) {
            log.info("이미 처리된 출고 완료 이벤트, 스킵: eventId={}, orderId={}", eventIdValue, orderId);
            return;
        }

        productConsumedEventJpaRepository.save(ProductConsumedEvent.builder()
                .eventId(eventIdValue)
                .eventType(ConsumedEventType.OUTBOUND_COMPLETED)
                .build());

        productInventoryService.finalizeOutbound(items);
    }
}
