package com.kurly.order.infrastructure.repository;

import com.kurly.order.domain.cart.CartItem;
import com.kurly.order.domain.cart.CartItemRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemJpaRepository extends CartItemRepository, JpaRepository<CartItem, Long> {
}
