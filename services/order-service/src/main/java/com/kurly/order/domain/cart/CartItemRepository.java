package com.kurly.order.domain.cart;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository {

    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    void deleteByCartIdAndProductId(Long cartId, Long productId);

    void deleteAllByCartIdAndProductIdIn(Long cartId, List<Long> productIds);
}