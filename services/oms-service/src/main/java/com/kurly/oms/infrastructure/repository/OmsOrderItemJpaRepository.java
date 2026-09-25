package com.kurly.oms.infrastructure.repository;

import com.kurly.oms.domain.order.OmsOrderItem;
import com.kurly.oms.domain.order.OmsOrderItemRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OmsOrderItemJpaRepository extends JpaRepository<OmsOrderItem, Long>, OmsOrderItemRepository {
}
