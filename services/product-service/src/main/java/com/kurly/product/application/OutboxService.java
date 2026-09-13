package com.kurly.product.application;

import com.kurly.product.infrastructure.entity.ProductOutbox;
import com.kurly.product.infrastructure.jpa.ProductOutboxJpaRepository;
import com.kurly.product.infrastructure.messaging.ProductOutboxProperties;
import com.kurly.product.infrastructure.messaging.dto.ProductInventoryConfirmedEvent;
import com.kurly.product.infrastructure.messaging.dto.ProductInventoryConfirmedEvent.FailedItemInfo;
import com.kurly.product.infrastructure.messaging.dto.ProductInventoryRestoredEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private static final String CONFIRMED_ROUTING_KEY = "product.inventory.confirmed";
    private static final String CONFIRMED_TYPE_ID = "com.kurly.product.infrastructure.messaging.dto.ProductInventoryConfirmedEvent";
    private static final String RESTORED_ROUTING_KEY = "product.inventory.restored";
    private static final String RESTORED_TYPE_ID = "com.kurly.product.infrastructure.messaging.dto.ProductInventoryRestoredEvent";

    private final ProductOutboxJpaRepository productOutboxJpaRepository;
    private final ProductOutboxProperties productOutboxProperties;
    private final ObjectMapper objectMapper;

    /** 재고 확정 성공. 호출자(재고 확정)의 트랜잭션과 같은 트랜잭션에서 커밋된다. */
    @Transactional
    public void recordConfirmed(Long orderId) {
        saveConfirmedOutbox(orderId, ProductInventoryConfirmedEvent.Status.CONFIRMED, null);
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordConfirmFailed(Long orderId, ProductInventoryConfirmedEvent.Status status, List<FailedItemInfo> failedItems) {
        saveConfirmedOutbox(orderId, status, failedItems);
    }

    @Transactional
    public void recordRestored(Long orderId) {
        saveRestoredOutbox(orderId, ProductInventoryRestoredEvent.Status.RESTORED);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRestoreFailed(Long orderId, ProductInventoryRestoredEvent.Status status) {
        saveRestoredOutbox(orderId, status);
    }

    private void saveConfirmedOutbox(Long orderId, ProductInventoryConfirmedEvent.Status status, List<FailedItemInfo> failedItems) {
        UUID eventId = UUID.randomUUID();
        ProductInventoryConfirmedEvent payload = new ProductInventoryConfirmedEvent(
                eventId, CONFIRMED_ROUTING_KEY, orderId, status, failedItems, LocalDateTime.now());

        persist(eventId, CONFIRMED_ROUTING_KEY, CONFIRMED_TYPE_ID, payload);
    }

    private void saveRestoredOutbox(Long orderId, ProductInventoryRestoredEvent.Status status) {
        UUID eventId = UUID.randomUUID();
        ProductInventoryRestoredEvent payload = new ProductInventoryRestoredEvent(
                eventId, RESTORED_ROUTING_KEY, orderId, status, LocalDateTime.now());

        persist(eventId, RESTORED_ROUTING_KEY, RESTORED_TYPE_ID, payload);
    }

    private void persist(UUID eventId, String routingKey, String typeId, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new IllegalStateException("아웃박스 이벤트 직렬화 실패: " + typeId, e);
        }

        ProductOutbox outboxEvent = ProductOutbox.builder()
                .eventId(eventId.toString())
                .exchange(productOutboxProperties.exchange())
                .routingKey(routingKey)
                .typeId(typeId)
                .payload(json)
                .build();

        productOutboxJpaRepository.save(outboxEvent);
    }
}
