package com.kurly.order.infrastructure.repository;

import com.kurly.order.domain.order.Order;
import com.kurly.order.domain.order.OrderRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderJpaRepository extends OrderRepository, JpaRepository<Order, Long> {
}
