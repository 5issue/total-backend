package com.kurly.wms.application;

import com.kurly.wms.infrastructure.entity.Inventory;
import com.kurly.wms.infrastructure.entity.Location.Zone;
import com.kurly.wms.infrastructure.jpa.InventoryJpaRepository;
import com.kurly.wms.infrastructure.jpa.ProductInventorySummaryProjection;
import com.kurly.wms.presentation.dto.InventoryDetailResponse;
import com.kurly.wms.presentation.dto.PageResponse;
import com.kurly.wms.presentation.dto.ProductInventorySummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;

    private final InventoryJpaRepository inventoryJpaRepository;

    @Transactional(readOnly = true)
    public PageResponse<ProductInventorySummaryResponse> summarize(Long warehouseId, Long productId, Integer page, Integer size) {
        Page<ProductInventorySummaryProjection> result =
                inventoryJpaRepository.summarize(warehouseId, productId, resolvePageable(page, size));

        return PageResponse.from(result.map(projection -> ProductInventorySummaryResponse.of(projection, warehouseId)));
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryDetailResponse> search(Long warehouseId, Long productId, Zone zone,
                                                          Long locationId, String lotNo, Integer page, Integer size) {
        Page<Inventory> result =
                inventoryJpaRepository.search(warehouseId, productId, zone, locationId, lotNo, resolvePageable(page, size));

        return PageResponse.from(result.map(InventoryDetailResponse::from));
    }

    // TODO: api-spec의 sort 파라미터는 아직 정렬에 반영하지 않는다(현재는 id/productId 오름차순 고정).
    private Pageable resolvePageable(Integer page, Integer size) {
        int effectivePage = page != null && page >= 0 ? page : 0;
        int effectiveSize = Math.min(size != null && size > 0 ? size : DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
        return PageRequest.of(effectivePage, effectiveSize);
    }
}
