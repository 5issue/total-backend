package com.kurly.order.infrastructure.repository;

import com.kurly.order.domain.claim.OrderClaim;
import com.kurly.order.domain.claim.OrderClaimRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import com.kurly.order.domain.order.Order;

public interface OrderClaimJpaRepository extends OrderClaimRepository, JpaRepository<OrderClaim, Long> {

    Optional<OrderClaim> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    @Override
    @Query("select c from OrderClaim c join fetch c.order o where o.memberId = :memberId " +
           "and (:claimType is null or c.claimType = :claimType) and (:status is null or c.status = :status)")
    Page<OrderClaim> findClaims(@Param("memberId") Long memberId, @Param("claimType") com.kurly.order.domain.claim.ClaimType claimType,
                                @Param("status") com.kurly.order.domain.claim.ClaimStatus status, Pageable pageable);


    @Query(value = """
            SELECT c FROM OrderClaim c
            JOIN FETCH c.order o
            WHERE c.claimType = 'RETURN'
            AND (:status IS NULL OR CAST(c.status AS string) = :status)
            AND (:filterStorage = false OR EXISTS (
                SELECT 1 FROM OrderItem i WHERE i.order = o
                AND CAST(i.storageType AS string) IN :storageTypes))
            ORDER BY c.requestedAt DESC
            """,
            countQuery = """
            SELECT COUNT(c) FROM OrderClaim c
            WHERE c.claimType = 'RETURN'
            AND (:status IS NULL OR CAST(c.status AS string) = :status)
            AND (:filterStorage = false OR EXISTS (
                SELECT 1 FROM OrderItem i WHERE i.order = c.order
                AND CAST(i.storageType AS string) IN :storageTypes))
            """)
    Page<OrderClaim> searchReturns(
            @Param("status") String status,
            @Param("filterStorage") boolean filterStorage,
            @Param("storageTypes") List<String> storageTypes,
            Pageable pageable
    );

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id IN :orderIds")
    List<Order> findOrdersWithItems(@Param("orderIds") List<Long> orderIds);

    @Query("""
            SELECT DISTINCT c FROM OrderClaim c
            LEFT JOIN FETCH c.attachments
            JOIN FETCH c.order o
            LEFT JOIN FETCH o.items
            WHERE c.id = :id AND c.claimType = 'RETURN'
            """)
    Optional<OrderClaim> findByIdWithAttachments(@Param("id") Long id);
}
