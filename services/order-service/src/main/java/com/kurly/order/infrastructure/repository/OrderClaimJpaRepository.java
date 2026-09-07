package com.kurly.order.infrastructure.repository;

import com.kurly.order.domain.claim.OrderClaim;
import com.kurly.order.domain.claim.OrderClaimRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderClaimJpaRepository extends OrderClaimRepository, JpaRepository<OrderClaim, Long> {

    Optional<OrderClaim> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    @Override
    @Query("select c from OrderClaim c join fetch c.order o where o.memberId = :memberId " +
            "and (:claimType is null or c.claimType = :claimType) and (:status is null or c.status = :status)")
    Page<OrderClaim> findClaims(@Param("memberId") Long memberId, @Param("claimType") com.kurly.order.domain.claim.ClaimType claimType,
                                @Param("status") com.kurly.order.domain.claim.ClaimStatus status, Pageable pageable);
}
