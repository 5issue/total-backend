package com.kurly.order.infrastructure.repository;

import com.kurly.order.domain.order.Order;
import com.kurly.order.domain.order.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends OrderRepository, JpaRepository<Order, Long> {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    @Override
    Optional<Order> findByIdAndMemberId(Long id, Long memberId);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.memberId = :memberId and o.status = 'CHECKOUT_CREATED'")
    Optional<Order> findActiveCheckoutForUpdate(@Param("memberId") Long memberId);

    @Override
    @Query(value = "select distinct o from Order o join o.items i where o.memberId = :memberId " +
            "and o.createdAt >= :from and o.status not in ('CHECKOUT_CREATED', 'PENDING_PAYMENT') " +
            "and (:productName is null or lower(i.productName) like lower(concat('%', :productName, '%')))",
            countQuery = "select count(distinct o) from Order o join o.items i where o.memberId = :memberId " +
                    "and o.createdAt >= :from and o.status not in ('CHECKOUT_CREATED', 'PENDING_PAYMENT') " +
                    "and (:productName is null or lower(i.productName) like lower(concat('%', :productName, '%')))")
    Page<Order> findOrders(@Param("memberId") Long memberId, @Param("from") LocalDateTime from,
                           @Param("productName") String productName, Pageable pageable);

    @Override
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Order o set o.status = 'PAID', o.paymentId = :paymentId, o.paidAt = :paidAt, " +
            "o.inventoryReservationToken = null where o.id = :orderId and o.status = 'PENDING_PAYMENT' " +
            "and o.inventoryReservedUntil >= :now")
    int completePayment(@Param("orderId") Long orderId, @Param("paymentId") Long paymentId,
                        @Param("paidAt") LocalDateTime paidAt, @Param("now") LocalDateTime now);

    @Override
    @Query("select o.id from Order o where o.status = 'PENDING_PAYMENT' and o.inventoryReservedUntil < :now")
    List<Long> findExpiredPaymentOrderIds(@Param("now") LocalDateTime now);

    @Override
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Order o set o.status = 'EXPIRED' where o.id = :orderId " +
            "and o.status = 'PENDING_PAYMENT' and o.inventoryReservedUntil < :now")
    int expirePayment(@Param("orderId") Long orderId, @Param("now") LocalDateTime now);
}
