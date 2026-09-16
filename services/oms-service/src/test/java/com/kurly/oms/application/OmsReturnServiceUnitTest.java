package com.kurly.oms.application;

import com.kurly.oms.domain.common.StorageType;
import com.kurly.oms.domain.order.OmsOrder;
import com.kurly.oms.domain.order.OmsOrderItem;
import com.kurly.oms.domain.order.OmsOrderRepository;
import com.kurly.oms.domain.returnorder.OmsReturnProcess;
import com.kurly.oms.domain.returnorder.OmsReturnProcessRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OmsReturnServiceUnitTest {

    @Mock OmsReturnProcessRepository processRepository;
    @Mock OmsOrderRepository orderRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    @Test
    void 반품_원장을_복제하지_않고_기존_OMS주문을_참조하는_프로세스만_저장한다() {
        OmsOrder omsOrder = order(StorageType.CHILLED);
        when(orderRepository.findByOrderId(2L)).thenReturn(Optional.of(omsOrder));
        OmsReturnService service = new OmsReturnService(processRepository, orderRepository, eventPublisher);

        service.receive(command("event-1"));

        ArgumentCaptor<OmsReturnProcess> captor = ArgumentCaptor.forClass(OmsReturnProcess.class);
        verify(processRepository).save(captor.capture());
        assertThat(captor.getValue().getOmsOrder()).isSameAs(omsOrder);
        assertThat(captor.getValue().isColdChain()).isTrue();
    }

    @Test
    void 온도대는_이벤트_복사본이_아니라_OMS주문상품에서_판단한다() {
        OmsOrder omsOrder = order(StorageType.ROOM);
        when(orderRepository.findByOrderId(2L)).thenReturn(Optional.of(omsOrder));
        OmsReturnService service = new OmsReturnService(processRepository, orderRepository, eventPublisher);

        service.receive(command("event-2"));

        ArgumentCaptor<OmsReturnProcess> captor = ArgumentCaptor.forClass(OmsReturnProcess.class);
        verify(processRepository).save(captor.capture());
        assertThat(captor.getValue().isColdChain()).isFalse();
    }

    @Test
    void 이미_처리한_이벤트는_다시_저장하지_않는다() {
        when(processRepository.existsBySourceEventId("event-3")).thenReturn(true);
        OmsReturnService service = new OmsReturnService(processRepository, orderRepository, eventPublisher);

        service.receive(command("event-3"));

        verify(processRepository, never()).save(any());
        verifyNoInteractions(orderRepository);
    }

    private OmsReturnService.ReturnRequestedCommand command(String eventId) {
        return new OmsReturnService.ReturnRequestedCommand(eventId, 1L, 2L, 3L, 4L,
                "RTN02", "상품 불량", 10000L);
    }

    private OmsOrder order(StorageType storageType) {
        OmsOrderItem item = OmsOrderItem.create(10L, 20L, 30L, storageType, 1, null, null);
        return OmsOrder.create(2L, "O202609170001", "sales-event", "홍길동", "01012345678",
                "06234", "서울시", null, List.of(item));
    }
}
