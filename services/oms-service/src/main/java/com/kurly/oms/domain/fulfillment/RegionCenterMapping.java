package com.kurly.oms.domain.fulfillment;

import com.kurly.oms.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

@Getter
@Entity
@Table(name = "region_center_mappings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionCenterMapping extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long regionId;

    @Column(nullable = false)
    private Long centerId;

    @Column(nullable = false)
    private Integer priority;

    @Column(nullable = false)
    private Boolean isActive;

    @Builder(access = AccessLevel.PRIVATE)
    private RegionCenterMapping(Long regionId, Long centerId, Integer priority, Boolean isActive) {
        this.regionId = regionId;
        this.centerId = centerId;
        this.priority = priority;
        this.isActive = isActive;
    }

    public static RegionCenterMapping create(Long regionId, Long centerId, Integer priority) {
        Assert.notNull(regionId, "배송 권역 ID는 필수입니다.");
        Assert.notNull(centerId, "출고 센터 ID는 필수입니다.");
        Assert.isTrue(priority != null && priority > 0, "우선순위는 1 이상이어야 합니다.");

        return RegionCenterMapping.builder()
                .regionId(regionId)
                .centerId(centerId)
                .priority(priority)
                .isActive(true)
                .build();
    }

}