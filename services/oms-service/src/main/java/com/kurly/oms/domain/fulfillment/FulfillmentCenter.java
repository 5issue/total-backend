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
@Table(name = "fulfillment_centers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FulfillmentCenter extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long warehouseId;

    @Column(nullable = false, unique = true, length = 30)
    private String centerCode;

    @Column(nullable = false, length = 100)
    private String centerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CenterStatus status;

    @Builder(access = AccessLevel.PRIVATE)
    private FulfillmentCenter(Long warehouseId, String centerCode, String centerName, CenterStatus status) {
        this.warehouseId = warehouseId;
        this.centerCode = centerCode;
        this.centerName = centerName;
        this.status = status;
    }

    public static FulfillmentCenter create(Long warehouseId, String centerCode, String centerName) {
        Assert.notNull(warehouseId, "WMS 창고 ID는 필수입니다.");
        Assert.hasText(centerCode, "센터 코드는 필수입니다.");
        Assert.hasText(centerName, "센터명은 필수입니다.");

        return FulfillmentCenter.builder()
                .warehouseId(warehouseId)
                .centerCode(centerCode)
                .centerName(centerName)
                .status(CenterStatus.ACTIVE)
                .build();
    }

}