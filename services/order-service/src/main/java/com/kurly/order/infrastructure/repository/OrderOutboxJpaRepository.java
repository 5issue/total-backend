package com.kurly.order.infrastructure.repository;

import com.kurly.order.domain.outbox.OrderOutbox;
import com.kurly.order.domain.outbox.OrderOutboxRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderOutboxJpaRepository extends OrderOutboxRepository, JpaRepository<OrderOutbox, Long> {
}
