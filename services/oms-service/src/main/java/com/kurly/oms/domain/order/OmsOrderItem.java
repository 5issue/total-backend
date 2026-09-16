package com.kurly.oms.domain.order;

import com.kurly.oms.domain.common.BaseEntity;
import com.kurly.oms.domain.common.StorageType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "oms_order_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OmsOrderItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "oms_order_id", nullable = false)
    private OmsOrder omsOrder;

    @Column(nullable = false)
    private Long orderItemId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Long skuId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StorageType storageType;

    @Column(nullable = false)
    private Integer quantity;

    @Column(precision = 12, scale = 2)
    private BigDecimal volumeCm3;

    private Integer weightGram;

    @Builder(access = AccessLevel.PRIVATE)
    private OmsOrderItem(Long orderItemId, Long productId, Long skuId,
                         StorageType storageType, Integer quantity,
                         BigDecimal volumeCm3, Integer weightGram) {
        this.orderItemId = orderItemId;
        this.productId = productId;
        this.skuId = skuId;
        this.storageType = storageType;
        this.quantity = quantity;
        this.volumeCm3 = volumeCm3;
        this.weightGram = weightGram;
    }

    public static OmsOrderItem create(
            Long orderItemId,
            Long productId,
            Long skuId,
            StorageType storageType,
            Integer quantity,
            BigDecimal volumeCm3,
            Integer weightGram
    ) {
        Assert.notNull(orderItemId, "원 주문 상품 ID는 필수입니다.");
        Assert.notNull(productId, "상품 ID는 필수입니다.");
        Assert.notNull(skuId, "SKU ID는 필수입니다.");
        Assert.notNull(storageType, "보관 온도대는 필수입니다.");
        Assert.isTrue(quantity != null && quantity > 0, "수량은 1개 이상이어야 합니다.");

        return OmsOrderItem.builder()
                .orderItemId(orderItemId)
                .productId(productId)
                .skuId(skuId)
                .storageType(storageType)
                .quantity(quantity)
                .volumeCm3(volumeCm3)
                .weightGram(weightGram)
                .build();
    }

    void assignOmsOrder(OmsOrder omsOrder) {
        this.omsOrder = omsOrder;
    }
}