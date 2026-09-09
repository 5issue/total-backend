package com.kurly.order.infrastructure.repository;

import com.kurly.order.domain.order.OrderItem;
import com.kurly.order.domain.order.OrderItemRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemJpaRepository extends OrderItemRepository, JpaRepository<OrderItem, Long> {
}
