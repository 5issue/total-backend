package com.kurly.order.infrastructure.repository;

import com.kurly.order.domain.cart.Cart;
import com.kurly.order.domain.cart.CartRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartJpaRepository extends CartRepository, JpaRepository<Cart, Long> {

    Optional<Cart> findByMemberId(Long memberId);
}
