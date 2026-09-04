package com.kurly.order.infrastructure.repository;

import com.kurly.order.domain.cart.Cart;
import com.kurly.order.domain.cart.CartRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartJpaRepository extends CartRepository, JpaRepository<Cart, Long> {
}
