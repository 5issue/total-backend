package com.kurly.order.domain.cart;

import java.util.Optional;

public interface CartRepository {

    Cart save(Cart cart);

    Optional<Cart> findByMemberId(Long memberId);
}
