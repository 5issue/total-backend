package com.kurly.oms.domain.order;

import java.util.Optional;

public interface OmsOrderItemRepository {

    Optional<OmsOrderItem> findById(Long id);
}
