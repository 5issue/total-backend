package com.kurly.oms.domain.fulfillment;

import com.kurly.oms.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import java.time.LocalDate;

@Getter
@Entity
@Table(name = "capacity_plans")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CapacityPlan extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long centerId;

    @Column(nullable = false)
    private Long slotId;

    @Column(nullable = false)
    private LocalDate serviceDate;

    @Column(nullable = false)
    private Integer baseCapacity;

    @Column(nullable = false)
    private Integer adjustedCapacity;

    @Column(nullable = false)
    private Integer reservedCapacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CapacityStatus status;

    @Column(length = 64)
    private String wfmEventId;

    @Builder(access = AccessLevel.PRIVATE)
    private CapacityPlan(Long centerId, Long slotId, LocalDate serviceDate,
                         Integer baseCapacity, Integer adjustedCapacity, Integer reservedCapacity,
                         CapacityStatus status, String wfmEventId) {
        this.centerId = centerId;
        this.slotId = slotId;
        this.serviceDate = serviceDate;
        this.baseCapacity = baseCapacity;
        this.adjustedCapacity = adjustedCapacity;
        this.reservedCapacity = reservedCapacity;
        this.status = status;
        this.wfmEventId = wfmEventId;
    }

    public static CapacityPlan create(Long centerId, Long slotId, LocalDate serviceDate, Integer baseCapacity) {
        Assert.notNull(centerId, "센터 ID는 필수입니다.");
        Assert.notNull(slotId, "배송 회차 ID는 필수입니다.");
        Assert.notNull(serviceDate, "배송 기준일은 필수입니다.");
        Assert.isTrue(baseCapacity != null && baseCapacity >= 0, "기본 CAPA는 0 이상이어야 합니다.");

        return CapacityPlan.builder()
                .centerId(centerId)
                .slotId(slotId)
                .serviceDate(serviceDate)
                .baseCapacity(baseCapacity)
                .adjustedCapacity(baseCapacity)
                .reservedCapacity(0)
                .status(CapacityStatus.OPEN)
                .build();
    }
}