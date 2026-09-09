package com.kurly.order.domain.order;

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
@Table(name = "order_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Long dealProductId;

    @Column(nullable = false)
    private Long skuId;

    @Column(nullable = false, length = 200)
    private String productName;

    @Column(length = 150)
    private String optionName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StorageType storageType;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Long unitPrice;

    @Column(nullable = false)
    private Long lineAmount;

    @Builder(access = AccessLevel.PRIVATE)
    private OrderItem(Long productId, Long dealProductId, Long skuId, String productName,
                      String optionName, StorageType storageType, Integer quantity,
                      Long unitPrice, Long lineAmount) {
        this.productId = productId;
        this.dealProductId = dealProductId;
        this.skuId = skuId;
        this.productName = productName;
        this.optionName = optionName;
        this.storageType = storageType;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineAmount = lineAmount;
    }

    public static OrderItem create(Long productId, Long dealProductId, Long skuId,
                                   String productName, String optionName, StorageType storageType,
                                   Integer quantity, Long unitPrice) {
        Assert.notNull(productId, "상품 ID는 필수입니다.");
        Assert.notNull(dealProductId, "딜 상품 ID는 필수입니다.");
        Assert.notNull(skuId, "SKU ID는 필수입니다.");
        Assert.hasText(productName, "상품명은 필수입니다.");
        Assert.notNull(storageType, "보관 온도는 필수입니다.");
        Assert.notNull(quantity, "수량은 필수입니다.");
        Assert.isTrue(quantity > 0, "수량은 1개 이상이어야 합니다.");
        Assert.notNull(unitPrice, "단가는 필수입니다.");

        return OrderItem.builder()
                .productId(productId)
                .dealProductId(dealProductId)
                .skuId(skuId)
                .productName(productName)
                .optionName(optionName)
                .storageType(storageType)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .lineAmount(unitPrice * quantity)
                .build();
    }

    void assignOrder(Order order) {
        this.order = order;
    }
}
