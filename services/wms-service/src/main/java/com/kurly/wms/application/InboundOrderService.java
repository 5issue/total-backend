package com.kurly.wms.application;

import com.kurly.common.exception.EntityNotFoundException;
import com.kurly.wms.domain.repository.InboundOrderRepository;
import com.kurly.wms.infrastructure.entity.InboundItem;
import com.kurly.wms.infrastructure.entity.InboundItem.InboundItemStatus;
import com.kurly.wms.infrastructure.entity.InboundOrder;
import com.kurly.wms.infrastructure.entity.InboundOrder.InboundOrderStatus;
import com.kurly.wms.infrastructure.entity.Warehouse;
import com.kurly.wms.infrastructure.entity.WmsProduct;
import com.kurly.wms.infrastructure.jpa.InboundItemJpaRepository;
import com.kurly.wms.infrastructure.jpa.WarehouseJpaRepository;
import com.kurly.wms.infrastructure.jpa.WmsProductJpaRepository;
import com.kurly.wms.presentation.dto.InboundAsnCreateRequest;
import com.kurly.wms.presentation.dto.InboundAsnItemRequest;
import com.kurly.wms.presentation.dto.InboundItemResponse;
import com.kurly.wms.presentation.dto.InboundOrderResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InboundOrderService {

    private final InboundOrderRepository inboundOrderRepository;
    private final InboundItemJpaRepository inboundItemJpaRepository;
    private final WarehouseJpaRepository warehouseJpaRepository;
    private final WmsProductJpaRepository wmsProductJpaRepository;

    @Transactional
    public InboundOrderResponse createAsn(InboundAsnCreateRequest request) {
        Warehouse warehouse = warehouseJpaRepository.findById(request.warehouseId())
                .orElseThrow(() -> new EntityNotFoundException("창고를 찾을 수 없습니다. warehouseId=" + request.warehouseId()));

        List<Long> productIds = request.items().stream()
                .map(InboundAsnItemRequest::productId)
                .distinct()
                .toList();

        Map<Long, WmsProduct> wmsProductMap = wmsProductJpaRepository.findAllById(productIds).stream().collect(Collectors.toMap(WmsProduct::getId, p -> p));
        if (wmsProductMap.size() != productIds.size()) {
            throw new EntityNotFoundException("상품을 찾을 수 없습니다. productIds=" + request.items().stream().map(InboundAsnItemRequest::productId).toList());
        }

        InboundOrder inboundOrder = inboundOrderRepository.save(InboundOrder.builder()
                .warehouse(warehouse)
                .poNumber(request.poNumber())
                .supplierName(request.supplierName())
                .expectedDate(request.expectedDate())
                .build());

        List<InboundItem> inboundItems = request.items().stream()
                .map(item -> InboundItem.builder()
                        .inboundOrder(inboundOrder)
                        .product(wmsProductMap.get(item.productId()))
                        .inboundUnit(item.inboundUnit())
                        .orderedQuantity(item.orderedQuantity())
                        .build()
                ).toList();
        List<InboundItem> savedInboundItems = inboundItemJpaRepository.saveAll(inboundItems);

        List<InboundItemResponse> itemResponses = savedInboundItems.stream()
                .map(InboundItemResponse::from)
                .toList();

        return InboundOrderResponse.of(inboundOrder, itemResponses);
    }
}
