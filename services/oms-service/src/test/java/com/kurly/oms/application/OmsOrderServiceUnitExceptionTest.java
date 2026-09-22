// src/test/java/com/kurly/oms/application/OmsOrderServiceUnitExceptionTest.java

package com.kurly.oms.application;

import com.kurly.common.exception.BusinessException;
import com.kurly.common.exception.GlobalErrorCode;
import com.kurly.oms.domain.common.OmsErrorCode;
import com.kurly.oms.domain.common.StorageType;
import com.kurly.oms.domain.fulfillment.DeliverySlotRepository;
import com.kurly.oms.domain.fulfillment.FulfillmentCenterRepository;
import com.kurly.oms.domain.fulfillment.TamRegion;
import com.kurly.oms.domain.fulfillment.TamRegionRepository;
import com.kurly.oms.domain.order.OmsOrder;
import com.kurly.oms.domain.order.OmsOrderItem;
import com.kurly.oms.domain.order.OmsOrderRepository;
import com.kurly.oms.domain.shipment.Shipment;
import com.kurly.oms.domain.shipment.ShipmentItem;
import com.kurly.oms.domain.shipment.ShipmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class OmsOrderServiceUnitExceptionTest {

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

    private OmsOrder createDefaultOrder(Long regionId, List<OmsOrderItem> items) {
        return OmsOrder.create(
                500L, "001", UUID.randomUUID().toString(), regionId, 10000L,
                "홍길동", "010-1234-5678", "06234", "서울시 강남구", "101호", items
        );
    }

    private OmsOrderItem createDefaultOrderItem() {
        OmsOrderItem item = OmsOrderItem.create(500L, 100L, 200L, StorageType.ROOM, 2, 5000L);
        ReflectionTestUtils.setField(item, "id", 10L);
        return item;
    }

    @Nested
    @DisplayName("주문 상세 조회 예외 (getOrderDetail)")
    class GetOrderDetailExceptionTest {

        @Test
        void 주문이_존재하지_않으면_OMS_ORDER_NOT_FOUND_예외가_발생한다() {
            // given
            when(omsOrderRepository.findByIdWithItems(anyLong())).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> omsOrderService.getOrderDetail(anyLong()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OmsErrorCode.OMS_ORDER_NOT_FOUND);
        }

        @Test
        void TamRegion이_존재하지_않으면_INTERNAL_SERVER_ERROR_예외가_발생한다(CapturedOutput output) {
            // given
            OmsOrder order = createDefaultOrder(99L, List.of(mock(OmsOrderItem.class)));

            when(omsOrderRepository.findByIdWithItems(anyLong())).thenReturn(Optional.of(order));
            when(tamRegionRepository.findById(99L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> omsOrderService.getOrderDetail(anyLong()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlobalErrorCode.INTERNAL_SERVER_ERROR);

            assertThat(output.getAll()).contains("[데이터 정합성 오류]");
            assertThat(output.getAll()).contains("TamRegion=99이 존재하지 않습니다.");
        }

        @Test
        void FulfillmentCenter가_존재하지_않으면_INTERNAL_SERVER_ERROR_예외가_발생한다(CapturedOutput output) {
            // given
            Long omsOrderId = 1L;
            OmsOrder order = createDefaultOrder(10L, List.of(mock(OmsOrderItem.class)));
            TamRegion regionMock = mock(TamRegion.class);
            Shipment shipmentMock = mock(Shipment.class);
            when(shipmentMock.getCenterId()).thenReturn(88L);

            when(omsOrderRepository.findByIdWithItems(omsOrderId)).thenReturn(Optional.of(order));
            when(tamRegionRepository.findById(10L)).thenReturn(Optional.of(regionMock));
            when(shipmentRepository.findByOmsOrderIdWithItems(omsOrderId)).thenReturn(List.of(shipmentMock));
            when(fulfillmentCenterRepository.findById(88L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> omsOrderService.getOrderDetail(omsOrderId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlobalErrorCode.INTERNAL_SERVER_ERROR);

            assertThat(output.getAll()).contains("FulfillmentCenter=88가 존재하지 않습니다.");
        }

        @Test
        void DeliverySlot이_존재하지_않으면_INTERNAL_SERVER_ERROR_예외가_발생한다(CapturedOutput output) {
            // given
            Long omsOrderId = 1L;
            OmsOrder order = createDefaultOrder(10L, List.of(mock(OmsOrderItem.class)));
            TamRegion regionMock = mock(TamRegion.class);
            Shipment shipmentMock = mock(Shipment.class);
            when(shipmentMock.getCenterId()).thenReturn(null);
            when(shipmentMock.getSlotId()).thenReturn(77L);

            when(omsOrderRepository.findByIdWithItems(omsOrderId)).thenReturn(Optional.of(order));
            when(tamRegionRepository.findById(10L)).thenReturn(Optional.of(regionMock));
            when(shipmentRepository.findByOmsOrderIdWithItems(omsOrderId)).thenReturn(List.of(shipmentMock));
            when(deliverySlotRepository.findById(77L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> omsOrderService.getOrderDetail(omsOrderId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlobalErrorCode.INTERNAL_SERVER_ERROR);

            assertThat(output.getAll()).contains("DeliverySlot=77이 존재하지 않습니다.");
        }

        @Test
        void ShipmentItem이_참조하는_OmsOrderItem이_주문에_존재하지_않으면_INTERNAL_SERVER_ERROR_예외가_발생한다(CapturedOutput output) {
            // given
            Long omsOrderId = 1L;
            OmsOrder order = createDefaultOrder(10L, List.of(createDefaultOrderItem()));
            TamRegion regionMock = mock(TamRegion.class);

            ShipmentItem shipmentItemMock = mock(ShipmentItem.class);
            when(shipmentItemMock.getOmsOrderItemId()).thenReturn(999L);

            Shipment shipmentMock = mock(Shipment.class);
            when(shipmentMock.getCenterId()).thenReturn(null);
            when(shipmentMock.getSlotId()).thenReturn(null);
            when(shipmentMock.getItems()).thenReturn(List.of(shipmentItemMock));

            when(omsOrderRepository.findByIdWithItems(omsOrderId)).thenReturn(Optional.of(order));
            when(tamRegionRepository.findById(10L)).thenReturn(Optional.of(regionMock));
            when(shipmentRepository.findByOmsOrderIdWithItems(omsOrderId)).thenReturn(List.of(shipmentMock));

            // when & then
            assertThatThrownBy(() -> omsOrderService.getOrderDetail(omsOrderId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlobalErrorCode.INTERNAL_SERVER_ERROR);

            assertThat(output.getAll()).contains("OmsOrderItem=999이 주문에 존재하지 않습니다.");
        }
    }

    @Nested
    @DisplayName("취소 가능 여부 확인 예외 (checkCancelEligibility)")
    class CheckCancelEligibilityExceptionTest {

        @Test
        void 주문이_존재하지_않으면_OMS_ORDER_NOT_FOUND_예외가_발생한다() {
            // given
            Long orderId = 999L;
            when(omsOrderRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> omsOrderService.checkCancelEligibility(orderId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OmsErrorCode.OMS_ORDER_NOT_FOUND);
        }
    }
}
