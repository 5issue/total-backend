package com.kurly.oms.infrastructure.repository;

import com.kurly.oms.domain.order.OmsOrder;
import com.kurly.oms.domain.order.OmsOrderRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OmsOrderJpaRepository extends JpaRepository<OmsOrder, Long>, OmsOrderRepository {
}
