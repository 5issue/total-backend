package com.kurly.oms.domain.shipment;

import com.kurly.oms.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

@Getter
@Entity
@Table(name = "shipment_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShipmentItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    @Column(nullable = false)
    private Long omsOrderItemId;

    @Column(nullable = false)
    private Integer quantity;

    @Builder(access = AccessLevel.PRIVATE)
    private ShipmentItem(Long omsOrderItemId, Integer quantity) {
        this.omsOrderItemId = omsOrderItemId;
        this.quantity = quantity;
    }

    public static ShipmentItem create(Long omsOrderItemId, Integer quantity) {
        Assert.notNull(omsOrderItemId, "OMS 주문 상품 ID는 필수입니다.");
        Assert.isTrue(quantity != null && quantity > 0, "할당 수량은 1개 이상이어야 합니다.");

        return ShipmentItem.builder()
                .omsOrderItemId(omsOrderItemId)
                .quantity(quantity)
                .build();
    }

    void assignShipment(Shipment shipment) {
        this.shipment = shipment;
    }
}