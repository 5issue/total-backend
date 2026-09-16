package com.kurly.oms.domain.fulfillment;

import com.kurly.oms.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import java.time.LocalTime;

@Getter
@Entity
@Table(name = "delivery_slots")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliverySlot extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long regionId;

    @Column(nullable = false, length = 30)
    private String slotCode;

    @Column(nullable = false, length = 80)
    private String slotName;

    @Column(nullable = false)
    private LocalTime cutoffTime;

    @Column(nullable = false)
    private LocalTime dispatchTime;

    @Column(nullable = false)
    private LocalTime deliveryStartTime;

    @Column(nullable = false)
    private LocalTime deliveryEndTime;

    @Column(nullable = false)
    private Integer leadDays;

    @Column(nullable = false)
    private Boolean isActive;

    @Builder(access = AccessLevel.PRIVATE)
    private DeliverySlot(Long regionId, String slotCode, String slotName,
                         LocalTime cutoffTime, LocalTime dispatchTime,
                         LocalTime deliveryStartTime, LocalTime deliveryEndTime,
                         Integer leadDays, Boolean isActive) {
        this.regionId = regionId;
        this.slotCode = slotCode;
        this.slotName = slotName;
        this.cutoffTime = cutoffTime;
        this.dispatchTime = dispatchTime;
        this.deliveryStartTime = deliveryStartTime;
        this.deliveryEndTime = deliveryEndTime;
        this.leadDays = leadDays != null ? leadDays : 0;
        this.isActive = isActive != null ? isActive : true;
    }

    public static DeliverySlot create(
            Long regionId,
            String slotCode,
            String slotName,
            LocalTime cutoffTime,
            LocalTime dispatchTime,
            LocalTime deliveryStartTime,
            LocalTime deliveryEndTime,
            Integer leadDays
    ) {
        Assert.notNull(regionId, "배송 권역 ID는 필수입니다.");
        Assert.hasText(slotCode, "회차 코드는 필수입니다.");
        Assert.hasText(slotName, "회차명은 필수입니다.");
        Assert.notNull(cutoffTime, "주문 마감 시각은 필수입니다.");
        Assert.notNull(dispatchTime, "출차 시각은 필수입니다.");
        Assert.notNull(deliveryStartTime, "배송 시작 시각은 필수입니다.");
        Assert.notNull(deliveryEndTime, "배송 종료 시각은 필수입니다.");

        return DeliverySlot.builder()
                .regionId(regionId)
                .slotCode(slotCode)
                .slotName(slotName)
                .cutoffTime(cutoffTime)
                .dispatchTime(dispatchTime)
                .deliveryStartTime(deliveryStartTime)
                .deliveryEndTime(deliveryEndTime)
                .leadDays(leadDays)
                .isActive(true)
                .build();
    }

}