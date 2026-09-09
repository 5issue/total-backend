package com.kurly.order.infrastructure.repository;

import com.kurly.order.domain.cart.Cart;
import com.kurly.order.domain.cart.CartRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface CartJpaRepository extends CartRepository, JpaRepository<Cart, Long> {

    @Override
    @Modifying
    @Query(value = "INSERT IGNORE INTO carts (member_id) VALUES (:memberId)", nativeQuery = true)
    void createIfAbsent(Long memberId);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.memberId = :memberId")
    Optional<Cart> findByMemberIdForUpdate(Long memberId);
}
