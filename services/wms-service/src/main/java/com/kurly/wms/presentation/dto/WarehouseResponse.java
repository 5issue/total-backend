package com.kurly.wms.presentation.dto;

import com.kurly.wms.domain.enums.Region;
import com.kurly.wms.infrastructure.entity.Warehouse;
import java.util.List;

public record WarehouseResponse(
        Long id,
        String code,
        String name,
        String address,
        List<Region> regions,
        Boolean isActive
) {
    public static WarehouseResponse from(Warehouse warehouse) {
        return new WarehouseResponse(
                warehouse.getId(),
                warehouse.getCode(),
                warehouse.getName(),
                warehouse.getAddress(),
                warehouse.getRegions(),
                warehouse.getIsActive()
        );
    }
}
