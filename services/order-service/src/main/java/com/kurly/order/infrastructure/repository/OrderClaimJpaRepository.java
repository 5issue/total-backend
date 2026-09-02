package com.kurly.order.infrastructure.repository;

import com.kurly.order.domain.claim.OrderClaim;
import com.kurly.order.domain.claim.OrderClaimRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderClaimJpaRepository extends OrderClaimRepository, JpaRepository<OrderClaim, Long> {
}
