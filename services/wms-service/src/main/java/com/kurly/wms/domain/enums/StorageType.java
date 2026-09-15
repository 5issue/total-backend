package com.kurly.wms.domain.enums;

import lombok.Getter;

/** 보관 유형. {@link com.kurly.wms.infrastructure.entity.WmsProduct}와 {@link com.kurly.wms.infrastructure.entity.Location} 양쪽에서 공유한다. */
@Getter
public enum StorageType {

    REFRIGERATED("냉장"),
    FROZEN("냉동"),
    ROOM_TEMPERATURE("상온");

    private final String label;

    StorageType(String label) {
        this.label = label;
    }
}
