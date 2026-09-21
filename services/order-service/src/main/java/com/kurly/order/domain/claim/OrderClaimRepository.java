package com.kurly.order.domain.claim;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface OrderClaimRepository {

    OrderClaim save(OrderClaim orderClaim);

    Optional<OrderClaim> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    Page<OrderClaim> findClaims(Long memberId, ClaimType claimType, ClaimStatus status, Pageable pageable);

    Optional<OrderClaim> findById(Long id);

    Optional<OrderClaim> findByIdWithAttachments(Long id);
}
