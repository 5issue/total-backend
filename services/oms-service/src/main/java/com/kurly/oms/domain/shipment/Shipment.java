package com.kurly.oms.domain.shipment;

import com.kurly.oms.domain.common.BaseEntity;
import com.kurly.oms.domain.common.StorageType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "shipments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shipment extends BaseEntity {

    @OneToMany(mappedBy = "shipment", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<ShipmentItem> items = new ArrayList<>();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long omsOrderId;

    @Column(nullable = false, unique = true, length = 40)
    private String shipmentNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StorageType storageType;

    private Long centerId;
    private Long slotId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ShipmentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private DeliveryStatus deliveryStatus;
    private LocalDate originalDispatchDate;
    private LocalDate expectedDispatchDate;
    private LocalDateTime rolledOverAt;

    @Column(length = 40)
    private String rolloverReasonCode;

    @Column(length = 30)
    private String boxType;

    @Column(nullable = false, length = 20)
    private String coolantType;

    @Column(nullable = false)
    private Integer coolantQuantity;

    @Column(length = 40)
    private String cancelReasonCode;

    @Column(length = 64)
    private String inventoryEventId;

    @Column(length = 40)
    private String inventoryFailureCode;

    @Column(length = 64)
    private String tmsDeliveryId;

    @Column(length = 64)
    private String lastDeliveryEventId;
    private LocalDateTime deliveredAt;

    @Builder(access = AccessLevel.PRIVATE)
    private Shipment(Long omsOrderId, String shipmentNo, StorageType storageType,
                     Long centerId, Long slotId, LocalDate expectedDispatchDate,
                     String coolantType, Integer coolantQuantity) {
        this.omsOrderId = omsOrderId;
        this.shipmentNo = shipmentNo;
        this.storageType = storageType;
        this.centerId = centerId;
        this.slotId = slotId;
        this.expectedDispatchDate = expectedDispatchDate;
        this.originalDispatchDate = expectedDispatchDate;
        this.status = ShipmentStatus.SHIPMENT_CREATED;
        this.coolantType = coolantType != null ? coolantType : "NONE";
        this.coolantQuantity = coolantQuantity != null ? coolantQuantity : 0;
    }

    public static Shipment create(
            Long omsOrderId,
            String shipmentNo,
            StorageType storageType,
            Long centerId,
            Long slotId,
            LocalDate expectedDispatchDate,
            List<ShipmentItem> items
    ) {
        Assert.notNull(omsOrderId, "OMS 주문 ID는 필수입니다.");
        Assert.hasText(shipmentNo, "Shipment 번호는 필수입니다.");
        Assert.notNull(storageType, "보관 온도대는 필수입니다.");
        Assert.notEmpty(items, "출고 품목은 최소 1개 이상이어야 합니다.");

        Shipment shipment = Shipment.builder()
                .omsOrderId(omsOrderId)
                .shipmentNo(shipmentNo)
                .storageType(storageType)
                .centerId(centerId)
                .slotId(slotId)
                .expectedDispatchDate(expectedDispatchDate)
                .build();

        for (ShipmentItem item : items) {
            shipment.items.add(item);
            item.assignShipment(shipment);
        }

        return shipment;
    }
}