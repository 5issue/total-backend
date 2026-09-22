package com.kurly.oms.application;

import com.kurly.oms.domain.common.StorageType;
import com.kurly.oms.domain.fulfillment.*;
import com.kurly.oms.domain.order.OmsOrder;
import com.kurly.oms.domain.order.OmsOrderItem;
import com.kurly.oms.domain.order.OmsOrderRepository;
import com.kurly.oms.domain.shipment.Shipment;
import com.kurly.oms.domain.shipment.ShipmentItem;
import com.kurly.oms.domain.shipment.ShipmentRepository;
import com.kurly.oms.infrastructure.messaging.OrderPaymentCompletedMessage;
import com.kurly.oms.presentation.dto.CancelEligibilityResponseDto;
import com.kurly.oms.presentation.dto.OmsOrderDetailResponse;
import com.kurly.oms.presentation.dto.OmsOrderListResponse;
import com.kurly.oms.presentation.dto.OmsOrderSearchCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OmsOrderServiceUnitTest {

    @Mock
    private OmsOrderRepository omsOrderRepository;

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private TamRegionRepository tamRegionRepository;

    @Mock
    private FulfillmentCenterRepository fulfillmentCenterRepository;

    @Mock
    private DeliverySlotRepository deliverySlotRepository;

    @InjectMocks
    private OmsOrderService omsOrderService;

    private OmsOrder createDefaultOrder(Long orderId, String orderNo, Long regionId, Long paidAmount, List<OmsOrderItem> items) {
        return OmsOrder.create(
                orderId, orderNo, UUID.randomUUID().toString(), regionId, paidAmount,
                "홍길동", "010-1234-5678", "06234", "서울시 강남구", "101호", items
        );
    }

    private OmsOrderItem createDefaultOrderItem() {
        OmsOrderItem item = OmsOrderItem.create(10L, 100L, 200L, StorageType.ROOM, 2, 5000L);
        ReflectionTestUtils.setField(item, "id", 10L);
        return item;
    }

    private OrderPaymentCompletedMessage createDefaultPaymentMessage() {
        OrderPaymentCompletedMessage.Item itemMsg = new OrderPaymentCompletedMessage.Item(
                10L, 100L, 200L, 2, 5000L, "ROOM"
        );
        OrderPaymentCompletedMessage.DeliveryAddress addressMsg = new OrderPaymentCompletedMessage.DeliveryAddress(
                "홍길동", "010-1234-5678", "06234", "서울시 강남구", "101호"
        );
        return new OrderPaymentCompletedMessage(
                UUID.randomUUID(), 500L, "O20260921001", 1L, 10L, 10000L,
                null, addressMsg, List.of(itemMsg)
        );
    }

    @Nested
    @DisplayName("주문 생성 (createOrder)")
    class CreateOrderTest {

        @Test
        void 결제완료_메시지_수신시_OMS주문을_정상_생성_및_저장한다() {
            // given
            OrderPaymentCompletedMessage event = createDefaultPaymentMessage();

            // when
            omsOrderService.createOrder(event);

            // then
            verify(omsOrderRepository, times(1)).save(any(OmsOrder.class));
        }
    }

    @Nested
    @DisplayName("주문 목록 조회 (listOrders)")
    class ListOrdersTest {

        @Test
        void 검색조건에_맞는_주문목록을_페이징하여_조회한다() {
            // given
            OmsOrderSearchCondition condition = new OmsOrderSearchCondition(null, null, null, null, null, null);
            Pageable pageable = Pageable.unpaged();
            when(omsOrderRepository.searchOrders(any(), any(), any(), any(), any(), any(), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of()));

            // when
            OmsOrderListResponse response = omsOrderService.listOrders(condition, pageable);

            // then
            assertThat(response).isNotNull();
            verify(omsOrderRepository).searchOrders(any(), any(), any(), any(), any(), any(), eq(pageable));
        }
    }

    @Nested
    @DisplayName("주문 상세 조회 (getOrderDetail)")
    class GetOrderDetailTest {

        @Test
        void 주문_권역_출고_센터_슬롯_정보가_모두_유효하면_상세정보를_정상_조회한다() {
            // given
            Long omsOrderId = 1L;
            OmsOrderItem orderItem = createDefaultOrderItem();
            OmsOrder order = createDefaultOrder(500L, "001", 10L, 10000L, List.of(orderItem));

            TamRegion region = TamRegion.create("SE01", "서울권역", DeliveryType.DAWN);

            ShipmentItem shipmentItem = ShipmentItem.create(10L, 2);
            Shipment shipment = Shipment.create(100L, "S001", StorageType.ROOM, 10L, 20L, LocalDate.now(), List.of(shipmentItem));
            shipment.getItems().add(shipmentItem);

            FulfillmentCenter center = FulfillmentCenter.create(101L, "GP01", "김포센터");
            DeliverySlot slot = DeliverySlot.create(1L, "SC01", "새벽배송 1회차", LocalTime.MAX, LocalTime.now(), LocalTime.MIN, LocalTime.MAX, 2);

            when(omsOrderRepository.findByIdWithItems(omsOrderId)).thenReturn(Optional.of(order));
            when(tamRegionRepository.findById(10L)).thenReturn(Optional.of(region));
            when(shipmentRepository.findByOmsOrderIdWithItems(omsOrderId)).thenReturn(List.of(shipment));
            when(fulfillmentCenterRepository.findById(10L)).thenReturn(Optional.of(center));
            when(deliverySlotRepository.findById(20L)).thenReturn(Optional.of(slot));

            // when
            OmsOrderDetailResponse response = omsOrderService.getOrderDetail(omsOrderId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.orderNo()).isEqualTo("001");
            assertThat(response.region().regionName()).isEqualTo("서울권역");
            assertThat(response.shipments()).hasSize(1);
            assertThat(response.shipments().getFirst().centerName()).isEqualTo("김포센터");
            assertThat(response.shipments().getFirst().slotName()).isEqualTo("새벽배송 1회차");
        }

        @Test
        void centerId와_slotId가_null이면_centerName과_slotName이_null로_조회된다() {
            // given
            Long omsOrderId = 1L;
            OmsOrderItem orderItem = createDefaultOrderItem();
            OmsOrder order = createDefaultOrder(500L, "001", 10L, 10000L, List.of(orderItem));

            TamRegion region = TamRegion.create("SE01", "서울권역", DeliveryType.DAWN);

            ShipmentItem shipmentItem = ShipmentItem.create(10L, 2);
            Shipment shipment = Shipment.create(100L, "S001", StorageType.ROOM, null, null, LocalDate.now(), List.of(shipmentItem));

            when(omsOrderRepository.findByIdWithItems(omsOrderId)).thenReturn(Optional.of(order));
            when(tamRegionRepository.findById(10L)).thenReturn(Optional.of(region));
            when(shipmentRepository.findByOmsOrderIdWithItems(omsOrderId)).thenReturn(List.of(shipment));

            // when
            OmsOrderDetailResponse response = omsOrderService.getOrderDetail(omsOrderId);

            // then
            assertThat(response.shipments().getFirst().centerName()).isNull();
            assertThat(response.shipments().getFirst().slotName()).isNull();
            verify(fulfillmentCenterRepository, never()).findById(any());
            verify(deliverySlotRepository, never()).findById(any());
        }


        @Test
        void 출고가_아직_할당되지_않으면_shipmentDetails가_빈_리스트로_조회된다() {
            // given
            Long omsOrderId = 1L;
            OmsOrderItem orderItem = createDefaultOrderItem();
            OmsOrder order = createDefaultOrder(500L, "001", 10L, 10000L, List.of(orderItem));

            TamRegion region = TamRegion.create("SE01", "서울권역", DeliveryType.DAWN);

            when(omsOrderRepository.findByIdWithItems(omsOrderId)).thenReturn(Optional.of(order));
            when(tamRegionRepository.findById(10L)).thenReturn(Optional.of(region));
            when(shipmentRepository.findByOmsOrderIdWithItems(omsOrderId)).thenReturn(List.of());

            // when
            OmsOrderDetailResponse response = omsOrderService.getOrderDetail(omsOrderId);

            // then
            assertThat(response.shipments()).isEmpty();
            verify(fulfillmentCenterRepository, never()).findById(any());
            verify(deliverySlotRepository, never()).findById(any());
        }
    }

    @Nested
    @DisplayName("취소 가능 여부 확인 (checkCancelEligibility)")
    class CheckCancelEligibilityTest {

        @Test
        void 주문이_존재하면_취소_가능_여부를_반환한다() {
            // given
            Long orderId = 500L;
            OmsOrderItem orderItem = createDefaultOrderItem();
            OmsOrder order = createDefaultOrder(orderId, "001", 10L, 5000L, List.of(orderItem));

            when(omsOrderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));

            // when
            CancelEligibilityResponseDto response = omsOrderService.checkCancelEligibility(orderId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.cancelable()).isTrue();
            verify(omsOrderRepository).findByOrderId(orderId);
        }
    }
}
