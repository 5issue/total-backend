package com.kurly.wms.application;

import com.kurly.wms.domain.enums.Region;
import com.kurly.wms.infrastructure.jpa.WarehouseJpaRepository;
import com.kurly.wms.presentation.dto.WarehouseResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WarehouseQueryService {

    private final WarehouseJpaRepository warehouseJpaRepository;

    @Transactional(readOnly = true)
    public List<WarehouseResponse> listByRegion(Region region, Boolean isActive) {
        boolean effectiveIsActive = (isActive != null) ? isActive : true;
        String regionName = (region != null) ? region.name() : null;
        return warehouseJpaRepository.search(regionName, effectiveIsActive).stream()
                .map(WarehouseResponse::from)
                .toList();
    }
}
