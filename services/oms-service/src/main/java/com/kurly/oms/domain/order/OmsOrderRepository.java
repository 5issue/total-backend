package com.kurly.oms.domain.order;

import java.util.Optional;

public interface OmsOrderRepository {
    OmsOrder save(OmsOrder omsOrder);

    Optional<OmsOrder> findById(Long orderId);

    Optional<OmsOrder> findByOrderId(Long orderId);
}
