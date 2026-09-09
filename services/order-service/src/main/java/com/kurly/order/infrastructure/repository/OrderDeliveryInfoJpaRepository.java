package com.kurly.order.infrastructure.repository;

import com.kurly.order.domain.order.OrderDeliveryInfo;
import com.kurly.order.domain.order.OrderDeliveryInfoRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderDeliveryInfoJpaRepository extends OrderDeliveryInfoRepository, JpaRepository<OrderDeliveryInfo, Long> {
}
