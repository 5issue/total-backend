package com.kurly.wms.infrastructure.entity;

import com.kurly.wms.domain.enums.StorageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * WMS 전용 상품 스냅샷. 상품 도메인의 Product를 직접 참조하지 않기 위한 로컬 마스터.
 * id는 상품 서비스의 Product UNIT ID와 동일한 값을 그대로 사용하므로 자체 채번하지 않는다.
 */
@Entity
@Table(name = "wms_product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WmsProduct {

    @Id
    private Long id;

    @Column(name = "sku_code", length = 50, nullable = false, unique = true)
    private String skuCode;

    @Column(name = "barcode", length = 50)
    private String barcode;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", length = 20, nullable = false)
    private StorageType storageType;

    @Column(name = "box_unit_qty", nullable = false)
    private Integer boxUnitQty;

    @Column(name = "pallet_box_qty", nullable = false)
    private Integer palletBoxQty;

    @Column(name = "safety_stock")
    private Integer safetyStock;

    @Builder
    private WmsProduct(Long id, String skuCode, String barcode, String name, StorageType storageType,
                        Integer boxUnitQty, Integer palletBoxQty, Integer safetyStock) {
        this.id = id;
        this.skuCode = skuCode;
        this.barcode = barcode;
        this.name = name;
        this.storageType = storageType;
        this.boxUnitQty = boxUnitQty;
        this.palletBoxQty = palletBoxQty;
        this.safetyStock = safetyStock;
    }

    /** 파레트 하나에 담기는 낱개(EA) 수량. 박스 없이 파레트에 직접 적재되는 상품은 boxUnitQty=1로 등록한다. */
    public int getEaPerPallet() {
        return palletBoxQty * boxUnitQty;
    }
}
