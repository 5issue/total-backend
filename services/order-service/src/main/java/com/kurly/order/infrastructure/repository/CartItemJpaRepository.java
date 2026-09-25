package com.kurly.order.infrastructure.repository;

import com.kurly.order.domain.cart.CartItem;
import com.kurly.order.domain.cart.CartItemRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CartItemJpaRepository extends JpaRepository<CartItem, Long>, CartItemRepository {

    @Override
    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    @Override
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM CartItem ci WHERE ci.cart.id = :cartId AND ci.productId = :productId")
    void deleteByCartIdAndProductId(@Param("cartId") Long cartId, @Param("productId") Long productId);

    @Override
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM CartItem ci WHERE ci.cart.id = :cartId AND ci.productId IN :productIds")
    void deleteAllByCartIdAndProductIdIn(@Param("cartId") Long cartId, @Param("productIds") List<Long> productIds);
}