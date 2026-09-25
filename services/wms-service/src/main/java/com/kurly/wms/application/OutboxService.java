package com.kurly.wms.application;

import com.kurly.wms.application.event.OutboxRecordedEvent;
import com.kurly.wms.infrastructure.entity.WmsOutbox;
import com.kurly.wms.infrastructure.jpa.WmsOutboxJpaRepository;
import com.kurly.wms.infrastructure.messaging.WmsOutboxProperties;
import com.kurly.wms.infrastructure.messaging.dto.InboundCompletedEvent;
import com.kurly.wms.infrastructure.messaging.dto.OutboundCompletedEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private static final String INBOUND_COMPLETED_ROUTING_KEY = "wms.inbound.completed";
    private static final String INBOUND_COMPLETED_TYPE_ID = "com.kurly.wms.infrastructure.messaging.dto.InboundCompletedEvent";
    private static final String OUTBOUND_COMPLETED_ROUTING_KEY = "wms.outbound.completed";
    private static final String OUTBOUND_COMPLETED_TYPE_ID = "com.kurly.wms.infrastructure.messaging.dto.OutboundCompletedEvent";

    private final WmsOutboxJpaRepository wmsOutboxJpaRepository;
    private final WmsOutboxProperties wmsOutboxProperties;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public void recordInboundCompleted(Long warehouseId, Long inboundOrderId, Long inboundItemId, Long productId,
                                        String lotNo, LocalDate expiredDate, int quantity) {
        UUID eventId = UUID.randomUUID();
        InboundCompletedEvent payload = new InboundCompletedEvent(
                eventId, INBOUND_COMPLETED_ROUTING_KEY, warehouseId, inboundOrderId, inboundItemId, productId,
                lotNo, expiredDate, quantity, LocalDateTime.now());

        persist(eventId, INBOUND_COMPLETED_ROUTING_KEY, INBOUND_COMPLETED_TYPE_ID, payload);
    }

    @Transactional
    public void recordOutboundCompleted(Long orderId, Long warehouseId, List<OutboundCompletedEvent.Item> items) {
        UUID eventId = UUID.randomUUID();
        OutboundCompletedEvent payload = new OutboundCompletedEvent(
                eventId, OUTBOUND_COMPLETED_ROUTING_KEY, orderId, warehouseId, items, LocalDateTime.now());

        persist(eventId, OUTBOUND_COMPLETED_ROUTING_KEY, OUTBOUND_COMPLETED_TYPE_ID, payload);
    }

    private void persist(UUID eventId, String routingKey, String typeId, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new IllegalStateException("아웃박스 이벤트 직렬화 실패: " + typeId, e);
        }

        WmsOutbox outboxEvent = WmsOutbox.builder()
                .eventId(eventId.toString())
                .exchange(wmsOutboxProperties.exchange())
                .routingKey(routingKey)
                .typeId(typeId)
                .payload(json)
                .build();

        wmsOutboxJpaRepository.save(outboxEvent);
        applicationEventPublisher.publishEvent(new OutboxRecordedEvent(outboxEvent.getId()));
    }
}
