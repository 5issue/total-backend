package com.kurly.order.infrastructure.repository;

import com.kurly.order.domain.claim.OrderClaim;
import com.kurly.order.domain.claim.OrderClaimRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderClaimJpaRepository extends OrderClaimRepository, JpaRepository<OrderClaim, Long> {

    Optional<OrderClaim> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    @Override
    @Query("select c from OrderClaim c join fetch c.order o where o.memberId = :memberId " +
           "and (:claimType is null or c.claimType = :claimType) and (:status is null or c.status = :status)")
    Page<OrderClaim> findClaims(@Param("memberId") Long memberId, @Param("claimType") com.kurly.order.domain.claim.ClaimType claimType,
                                @Param("status") com.kurly.order.domain.claim.ClaimStatus status, Pageable pageable);


    @Query("""
            SELECT DISTINCT c FROM OrderClaim c
            JOIN FETCH c.order o
            LEFT JOIN FETCH o.items
            WHERE c.claimType = 'RETURN'
            AND (:status IS NULL OR CAST(c.status AS string) = :status)
            ORDER BY c.requestedAt DESC
            """)
    Page<OrderClaim> searchReturns(
            @Param("status") String status,
            Pageable pageable
    );

    @Query("""
            SELECT DISTINCT c FROM OrderClaim c
            LEFT JOIN FETCH c.attachments
            JOIN FETCH c.order o
            LEFT JOIN FETCH o.items
            WHERE c.id = :id
            """)
    Optional<OrderClaim> findByIdWithAttachments(@Param("id") Long id);
}
