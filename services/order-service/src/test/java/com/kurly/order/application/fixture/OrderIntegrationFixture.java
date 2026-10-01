package com.kurly.order.application.fixture;

import com.kurly.order.domain.cart.Cart;
import com.kurly.order.domain.cart.CartItem;
import com.kurly.order.domain.common.StorageType;
import com.kurly.order.infrastructure.dto.AddressResponse;
import com.kurly.order.infrastructure.dto.CartProductInfo;

public final class OrderIntegrationFixture {

    private OrderIntegrationFixture() {
    }

    public static Cart cart(Long memberId, CartItem... items) {
        Cart cart = Cart.create(memberId);
        cart.updateDeliveryAddress(10L, 20L, null);
        for (CartItem item : items) {
            cart.addItem(item);
        }
        return cart;
    }

    public static CartItem item(Long productId, int quantity) {
        return CartItem.create(productId, StorageType.REFRIGERATED, quantity);
    }

    public static AddressResponse address() {
        return new AddressResponse(10L, "집", "홍길동", "01012345678", "06234", "서울시 강남구", "101호");
    }

    public static CartProductInfo product(Long productId, String name, Long salePrice) {
        return new CartProductInfo(productId, name, salePrice, null, StorageType.REFRIGERATED,
                "ON_SALE", null, new CartProductInfo.InventoryInfo(100, false, 10));
    }
}
