package com.kurly.wms.application;

import com.kurly.wms.infrastructure.entity.StockMovement.MovementStatus;
import com.kurly.wms.infrastructure.entity.StockMovement.MovementType;
import com.kurly.wms.infrastructure.jpa.StockMovementJpaRepository;
import com.kurly.wms.presentation.dto.StockMovementResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockMovementQueryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final StockMovementJpaRepository stockMovementJpaRepository;

    @Transactional(readOnly = true)
    public List<StockMovementResponse> list(Long warehouseId, MovementType movementType, MovementStatus status, Integer limit) {
        int effectiveLimit = Math.min(limit != null && limit > 0 ? limit : DEFAULT_LIMIT, MAX_LIMIT);

        return stockMovementJpaRepository.search(warehouseId, movementType, status, PageRequest.of(0, effectiveLimit))
                .stream()
                .map(StockMovementResponse::from)
                .toList();
    }
}
