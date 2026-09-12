package com.kurly.order.domain.order;

import java.util.Optional;

public interface OrderDeliveryInfoRepository {

    OrderDeliveryInfo save(OrderDeliveryInfo orderDeliveryInfo);

    Optional<OrderDeliveryInfo> findById(Long orderId);

}
