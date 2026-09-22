package com.kurly.oms.domain.order;

import com.kurly.oms.presentation.dto.OmsOrderSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Optional;


public interface OmsOrderRepository {
    OmsOrder save(OmsOrder omsOrder);

    Optional<OmsOrder> findById(Long orderId);

    Optional<OmsOrder> findByOrderId(Long orderId);

    Page<OmsOrderSummary> searchOrders(
            String orderNo,
            String status,
            Long regionId,
            Long centerId,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Pageable pageable
    );

    Optional<OmsOrder> findByIdWithItems(Long omsOrderId);

    boolean existsBySourceEventId(String sourceEventId);

    boolean existsByOrderId(Long orderId);
}
