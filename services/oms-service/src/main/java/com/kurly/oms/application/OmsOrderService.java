package com.kurly.oms.application;

import com.kurly.common.exception.BusinessException;
import com.kurly.common.exception.GlobalErrorCode;
import com.kurly.oms.domain.common.OmsErrorCode;
import com.kurly.oms.domain.common.StorageType;
import com.kurly.oms.domain.fulfillment.*;
import com.kurly.oms.domain.order.OmsOrder;
import com.kurly.oms.domain.order.OmsOrderItem;
import com.kurly.oms.domain.order.OmsOrderRepository;
import com.kurly.oms.domain.shipment.ShipmentRepository;
import com.kurly.oms.infrastructure.messaging.OrderPaymentCompletedMessage;
import com.kurly.oms.presentation.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OmsOrderService {

    private static final Set<String> DUPLICATE_CONSTRAINTS = Set.of(
            "uk_oms_orders_order_id", "uk_oms_orders_source_event_id");
    private final OmsOrderRepository omsOrderRepository;
    private final OmsOrderCreator orderCreator;
    private final ShipmentRepository shipmentRepository;
    private final TamRegionRepository tamRegionRepository;
    private final FulfillmentCenterRepository fulfillmentCenterRepository;
    private final DeliverySlotRepository deliverySlotRepository;

    public void createOrder(OrderPaymentCompletedMessage event) {

        if (omsOrderRepository.existsBySourceEventId(event.eventId().toString()) ||
            omsOrderRepository.existsByOrderId(event.orderId())
        ) {
            log.info("[OmsOrderService] 이미 처리된 주문 이벤트입니다. eventId: {}, orderId: {}", event.eventId(), event.orderId());
            return;
        }

        List<OmsOrderItem> items = event.items().stream()
                .map(item -> OmsOrderItem.create(
                        item.orderItemId(),
                        item.productId(),
                        item.skuId(),
                        StorageType.valueOf(item.storageType()),
                        item.quantity(),
                        item.unitPrice()
                ))
                .toList();

        OmsOrder omsOrder = OmsOrder.create(
                event.orderId(),
                event.orderNo(),
                event.eventId().toString(),
                event.regionId(),
                event.paidAmount(),
                event.deliveryAddress().recipientName(),
                event.deliveryAddress().phone(),
                event.deliveryAddress().zipCode(),
                event.deliveryAddress().address(),
                event.deliveryAddress().addressDetail(),
                items
        );

        try {
            orderCreator.create(omsOrder);
        } catch (DataIntegrityViolationException e) {
            if (!isDuplicateOrderConstraint(e) ||
                    (!omsOrderRepository.existsBySourceEventId(event.eventId().toString()) &&
                     !omsOrderRepository.existsByOrderId(event.orderId()))) {
                throw e;
            }
            log.info("[OmsOrderService] 동시 주문 생성 중복 eventId={}, orderId={}", event.eventId(), event.orderId());
        }
    }

    private boolean isDuplicateOrderConstraint(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return DUPLICATE_CONSTRAINTS.contains(violation.getConstraintName());
            }
        }
        return false;
    }

    @Transactional(readOnly = true)
    public OmsOrderListResponse listOrders(OmsOrderSearchCondition condition, Pageable pageable) {
        Page<OmsOrderSummary> result = omsOrderRepository.searchOrders(
                condition.orderNo(),
                condition.status(),
                condition.regionId(),
                condition.centerId(),
                condition.startAt(),
                condition.endAt(),
                pageable
        );
        return OmsOrderListResponse.from(result);
    }

    @Transactional(readOnly = true)
    public OmsOrderDetailResponse getOrderDetail(Long omsOrderId) {
        OmsOrder order = omsOrderRepository.findByIdWithItems(omsOrderId)
                .orElseThrow(() -> new BusinessException(OmsErrorCode.OMS_ORDER_NOT_FOUND));

        TamRegion region = tamRegionRepository.findById(order.getRegionId())
                .orElseThrow(() -> {
                    log.error(
                            "[OmsOrderService] [데이터 정합성 오류] OmsOrder={}의 TamRegion={}이 존재하지 않습니다.",
                            order.getId(),
                            order.getRegionId()
                    );
                    return new BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
                });

        Map<Long, OmsOrderItem> orderItemMap = order.getItems().stream()
                .collect(Collectors.toMap(OmsOrderItem::getId, Function.identity()));

        List<OmsOrderDetailResponse.ShipmentDetail> shipmentDetails =
                shipmentRepository.findByOmsOrderIdWithItems(omsOrderId).stream()
                        .map(shipment -> {
                            String centerName = shipment.getCenterId() == null
                                    ? null
                                    : fulfillmentCenterRepository.findById(shipment.getCenterId())
                                      .map(FulfillmentCenter::getCenterName)
                                      .orElseThrow(() -> {
                                          log.error(
                                                  "[OmsOrderService] [데이터 정합성 오류] Shipment={}의 FulfillmentCenter={}가 존재하지 않습니다.",
                                                  shipment.getId(),
                                                  shipment.getCenterId()
                                          );
                                          return new BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
                                      });

                            String slotName = shipment.getSlotId() == null
                                    ? null
                                    : deliverySlotRepository.findById(shipment.getSlotId())
                                      .map(DeliverySlot::getSlotName)
                                      .orElseThrow(() -> {
                                          log.error(
                                                  "[OmsOrderService] [데이터 정합성 오류] Shipment={}의 DeliverySlot={}이 존재하지 않습니다.",
                                                  shipment.getId(),
                                                  shipment.getSlotId()
                                          );
                                          return new BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
                                      });

                            List<OmsOrderDetailResponse.ShipmentItemDetail> items =
                                    shipment.getItems().stream()
                                            .map(shipmentItem -> {
                                                OmsOrderItem orderItem = orderItemMap.get(shipmentItem.getOmsOrderItemId());

                                                if (orderItem == null) {
                                                    log.error(
                                                            "[OmsOrderService] [데이터 정합성 오류] ShipmentItem={}이 참조하는 OmsOrderItem={}이 주문에 존재하지 않습니다.",
                                                            shipmentItem.getId(),
                                                            shipmentItem.getOmsOrderItemId()
                                                    );
                                                    throw new BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
                                                }

                                                return new OmsOrderDetailResponse.ShipmentItemDetail(
                                                        orderItem.getId(),
                                                        orderItem.getProductId(),
                                                        orderItem.getSkuId(),
                                                        shipmentItem.getQuantity()
                                                );
                                            })
                                            .toList();

                            return new OmsOrderDetailResponse.ShipmentDetail(
                                    shipment.getId(),
                                    shipment.getShipmentNo(),
                                    shipment.getStorageType().name(),
                                    shipment.getStatus().name(),
                                    centerName,
                                    slotName,
                                    items
                            );
                        })
                        .toList();

        return new OmsOrderDetailResponse(
                order.getId(),
                order.getOrderId(),
                order.getOrderNo(),
                order.getStatus().name(),
                new OmsOrderDetailResponse.RegionInfo(
                        region.getId(),
                        region.getRegionName(),
                        region.getDeliveryType().name()
                ),
                shipmentDetails
        );
    }


    // TODO: P2, 어드민 강제 주문 취소 API
    @Transactional
    public void cancelOrderByAdmin(Long orderId) {
    }

    @Transactional(readOnly = true)
    public CancelEligibilityResponseDto checkCancelEligibility(Long orderId) {
        OmsOrder omsOrder = omsOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(OmsErrorCode.OMS_ORDER_NOT_FOUND));

        return CancelEligibilityResponseDto.from(omsOrder);
    }
}