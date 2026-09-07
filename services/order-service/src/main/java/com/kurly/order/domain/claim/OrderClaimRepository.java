package com.kurly.order.domain.claim;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderClaimRepository {

    OrderClaim save(OrderClaim orderClaim);

    Optional<OrderClaim> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    Page<OrderClaim> findClaims(Long memberId, ClaimType claimType, ClaimStatus status, Pageable pageable);
}
