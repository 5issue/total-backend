package com.kurly.order.domain.cart;

import java.util.Optional;

public interface CartRepository {

    Cart save(Cart cart);

    void createIfAbsent(Long memberId);

    Optional<Cart> findByMemberIdForUpdate(Long memberId);
}
