package com.kurly.product.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kurly.product.domain.dto.ReserveItem;
import com.kurly.product.domain.enums.ConsumedEventType;
import com.kurly.product.infrastructure.entity.ProductConsumedEvent;
import com.kurly.product.infrastructure.jpa.ProductConsumedEventJpaRepository;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WmsEventServiceTest {

    @Mock
    private ProductConsumedEventJpaRepository productConsumedEventJpaRepository;
    @Mock
    private ProductInventoryService productInventoryService;

    private WmsEventService wmsEventService;

    @BeforeEach
    void setUp() {
        wmsEventService = new WmsEventService(productConsumedEventJpaRepository, productInventoryService);
    }

    @Test
    @DisplayName("처음 받는 이벤트면 인박스에 기록하고 재고를 늘린다")
    void handleInboundCompleted_firstTime_marksAndIncreasesStock() {
        UUID eventId = UUID.randomUUID();
        when(productConsumedEventJpaRepository.existsByEventId(eventId.toString())).thenReturn(false);

        wmsEventService.handleInboundCompleted(eventId, 1L, 10);

        ArgumentCaptor<ProductConsumedEvent> captor = ArgumentCaptor.forClass(ProductConsumedEvent.class);
        verify(productConsumedEventJpaRepository).save(captor.capture());
        Assertions.assertThat(captor.getValue().getEventId()).isEqualTo(eventId.toString());
        Assertions.assertThat(captor.getValue().getEventType()).isEqualTo(ConsumedEventType.INBOUND_COMPLETED);
        verify(productInventoryService).increaseStock(1L, 10);
    }

    @Test
    @DisplayName("이미 처리된 이벤트(재전달)면 재고를 다시 늘리지 않는다")
    void handleInboundCompleted_alreadyProcessed_skips() {
        UUID eventId = UUID.randomUUID();
        when(productConsumedEventJpaRepository.existsByEventId(eventId.toString())).thenReturn(true);

        wmsEventService.handleInboundCompleted(eventId, 1L, 10);

        verify(productConsumedEventJpaRepository, never()).save(any());
        verify(productInventoryService, never()).increaseStock(any(), anyInt());
    }

    @Test
    @DisplayName("처음 받는 출고 완료 이벤트면 인박스에 기록하고 재고를 정산한다")
    void handleOutboundCompleted_firstTime_marksAndFinalizesStock() {
        UUID eventId = UUID.randomUUID();
        List<ReserveItem> items = List.of(new ReserveItem(1L, 3));
        when(productConsumedEventJpaRepository.existsByEventId(eventId.toString())).thenReturn(false);

        wmsEventService.handleOutboundCompleted(eventId, 100L, items);

        ArgumentCaptor<ProductConsumedEvent> captor = ArgumentCaptor.forClass(ProductConsumedEvent.class);
        verify(productConsumedEventJpaRepository).save(captor.capture());
        Assertions.assertThat(captor.getValue().getEventId()).isEqualTo(eventId.toString());
        Assertions.assertThat(captor.getValue().getEventType()).isEqualTo(ConsumedEventType.OUTBOUND_COMPLETED);
        verify(productInventoryService).finalizeOutbound(items);
    }

    @Test
    @DisplayName("이미 처리된 출고 완료 이벤트(재전달)면 재고를 다시 정산하지 않는다")
    void handleOutboundCompleted_alreadyProcessed_skips() {
        UUID eventId = UUID.randomUUID();
        when(productConsumedEventJpaRepository.existsByEventId(eventId.toString())).thenReturn(true);

        wmsEventService.handleOutboundCompleted(eventId, 100L, List.of(new ReserveItem(1L, 3)));

        verify(productConsumedEventJpaRepository, never()).save(any());
        verify(productInventoryService, never()).finalizeOutbound(any());
    }
}
