package com.kurly.order.domain.cart;

import com.kurly.order.domain.common.BaseEntity;
import com.kurly.order.domain.common.StorageType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

@Getter
@Entity
@Table(name = "cart_items", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cart_items_cart_product", columnNames = {"cart_id", "productId"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @Column(nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StorageType storageType;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private boolean isChecked;

    @Builder(access = AccessLevel.PRIVATE)
    private CartItem(Long productId, StorageType storageType, Integer quantity, boolean isChecked) {
        this.productId = productId;
        this.storageType = storageType;
        this.quantity = quantity;
        this.isChecked = isChecked;
    }

    public static CartItem create(Long productId, StorageType storageType, Integer quantity) {
        Assert.notNull(productId, "상품 ID는 필수입니다.");
        Assert.notNull(storageType, "보관 온도는 필수입니다.");
        Assert.isTrue(quantity != null && quantity > 0, "수량은 1개 이상이어야 합니다.");

        return CartItem.builder()
                .productId(productId)
                .storageType(storageType)
                .quantity(quantity)
                .isChecked(true)
                .build();
    }

    public void changeQuantity(int quantity) {
        Assert.isTrue(quantity > 0, "수량은 1개 이상이어야 합니다.");
        this.quantity = quantity;
    }

    public void toggleChecked(boolean isChecked) {
        this.isChecked = isChecked;
    }

    void assignCart(Cart cart) {
        this.cart = cart;
    }
}
