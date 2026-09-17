package com.kurly.oms.application;

import com.kurly.common.exception.BusinessException;
import com.kurly.oms.domain.common.OmsErrorCode;
import com.kurly.oms.domain.common.StorageType;
import com.kurly.oms.domain.order.OmsOrder;
import com.kurly.oms.domain.order.OmsOrderItem;
import com.kurly.oms.domain.order.OmsOrderRepository;
import com.kurly.oms.infrastructure.messaging.OrderPaymentCompletedMessage;
import com.kurly.oms.presentation.dto.CancelEligibilityResponseDto;
import com.kurly.oms.presentation.dto.OmsOrderListResponse;
import com.kurly.oms.presentation.dto.OmsOrderSearchCondition;
import com.kurly.oms.presentation.dto.OmsOrderSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OmsOrderService {

    private final OmsOrderRepository omsOrderRepository;

    @Transactional
    public void createOrder(OrderPaymentCompletedMessage event) {
        List<OmsOrderItem> items = event.items().stream()
                .map(item -> OmsOrderItem.create(
                        item.orderItemId(),
                        item.productId(),
                        item.skuId(),
                        StorageType.valueOf(item.storageType()),
                        item.quantity()
                ))
                .toList();

        OmsOrder omsOrder = OmsOrder.create(
                event.orderId(),
                event.orderNo(),
                event.eventId().toString(),
                event.deliveryAddress().recipientName(),
                event.deliveryAddress().phone(),
                event.deliveryAddress().zipCode(),
                event.deliveryAddress().address(),
                event.deliveryAddress().addressDetail(),
                items
        );

        omsOrderRepository.save(omsOrder);
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
    public Object getOrderMonitoring(Long omsOrderId) {
        return null;
    }

    @Transactional
    public void cancelOrderByAdmin(Long orderId) {
    }

    @Transactional(readOnly = true)
    public CancelEligibilityResponseDto checkCancelEligibility(Long orderId) {
        OmsOrder omsOrder = omsOrderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OmsErrorCode.OMS_ORDER_NOT_FOUND));

        return CancelEligibilityResponseDto.from(omsOrder);
    }
}