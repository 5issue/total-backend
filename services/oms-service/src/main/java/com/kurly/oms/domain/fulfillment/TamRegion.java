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
@Table(name = "tam_regions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TamRegion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String regionCode;

    @Column(nullable = false, length = 100)
    private String regionName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryType deliveryType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegionStatus status;

    @Builder(access = AccessLevel.PRIVATE)
    private TamRegion(String regionCode, String regionName, DeliveryType deliveryType, RegionStatus status) {
        this.regionCode = regionCode;
        this.regionName = regionName;
        this.deliveryType = deliveryType;
        this.status = status;
    }

    public static TamRegion create(String regionCode, String regionName, DeliveryType deliveryType) {
        Assert.hasText(regionCode, "권역 코드는 필수입니다.");
        Assert.hasText(regionName, "권역명은 필수입니다.");
        Assert.notNull(deliveryType, "배송 유형은 필수입니다.");

        return TamRegion.builder()
                .regionCode(regionCode)
                .regionName(regionName)
                .deliveryType(deliveryType)
                .status(RegionStatus.ACTIVE)
                .build();
    }

}