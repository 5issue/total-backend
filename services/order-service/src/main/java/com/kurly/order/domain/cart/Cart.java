package com.kurly.order.domain.cart;

import com.kurly.order.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "carts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cart extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long memberId;

    private Long addressId;

    private Long regionId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DeliveryType deliveryType;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItem> items = new ArrayList<>();

    @Builder(access = AccessLevel.PRIVATE)
    private Cart(Long memberId, Long addressId, Long regionId, DeliveryType deliveryType) {
        this.memberId = memberId;
        this.addressId = addressId;
        this.regionId = regionId;
        this.deliveryType = deliveryType;
    }

    public static Cart create(Long memberId) {
        Assert.notNull(memberId, "회원 ID는 필수입니다.");
        return Cart.builder()
                .memberId(memberId)
                .build();
    }

    public void updateDeliveryAddress(Long addressId, Long regionId, DeliveryType deliveryType) {
        this.addressId = addressId;
        this.regionId = regionId;
        this.deliveryType = deliveryType;
    }

    public void addItem(CartItem item) {
        this.items.add(item);
        item.assignCart(this);
    }
}
