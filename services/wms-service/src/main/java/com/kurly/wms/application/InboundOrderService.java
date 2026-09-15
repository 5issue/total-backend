package com.kurly.wms.application;

<<<<<<< HEAD
import com.kurly.common.exception.BusinessException;
import com.kurly.common.exception.EntityNotFoundException;
import com.kurly.wms.domain.enums.StorageType;
import com.kurly.wms.domain.exception.WmsErrorCode;
import com.kurly.wms.domain.repository.InboundOrderRepository;
import com.kurly.wms.infrastructure.entity.InboundItem;
import com.kurly.wms.infrastructure.entity.InboundItem.InboundItemStatus;
import com.kurly.wms.infrastructure.entity.InboundItem.InboundUnit;
import com.kurly.wms.infrastructure.entity.InboundOrder;
import com.kurly.wms.infrastructure.entity.Inventory;
import com.kurly.wms.infrastructure.entity.Location;
import com.kurly.wms.infrastructure.entity.Location.LocationStatus;
import com.kurly.wms.infrastructure.entity.Location.LocationType;
import com.kurly.wms.infrastructure.entity.Location.Zone;
import com.kurly.wms.infrastructure.entity.StockMovement;
import com.kurly.wms.infrastructure.entity.StockMovement.MovementStatus;
import com.kurly.wms.infrastructure.entity.StockMovement.MovementType;
import com.kurly.wms.infrastructure.entity.StockMovement.MovementUnit;
import com.kurly.wms.infrastructure.entity.Warehouse;
import com.kurly.wms.infrastructure.entity.WmsProduct;
import com.kurly.wms.infrastructure.jpa.InboundItemJpaRepository;
import com.kurly.wms.infrastructure.jpa.InventoryJpaRepository;
import com.kurly.wms.infrastructure.jpa.LocationJpaRepository;
import com.kurly.wms.infrastructure.jpa.StockMovementJpaRepository;
=======
import com.kurly.common.exception.EntityNotFoundException;
import com.kurly.wms.domain.repository.InboundOrderRepository;
import com.kurly.wms.infrastructure.entity.InboundItem;
import com.kurly.wms.infrastructure.entity.InboundItem.InboundItemStatus;
import com.kurly.wms.infrastructure.entity.InboundOrder;
import com.kurly.wms.infrastructure.entity.InboundOrder.InboundOrderStatus;
import com.kurly.wms.infrastructure.entity.Warehouse;
import com.kurly.wms.infrastructure.entity.WmsProduct;
import com.kurly.wms.infrastructure.jpa.InboundItemJpaRepository;
>>>>>>> 40e0939e (feat: 입고 예정(입고 전표) 생성)
import com.kurly.wms.infrastructure.jpa.WarehouseJpaRepository;
import com.kurly.wms.infrastructure.jpa.WmsProductJpaRepository;
import com.kurly.wms.presentation.dto.InboundAsnCreateRequest;
import com.kurly.wms.presentation.dto.InboundAsnItemRequest;
import com.kurly.wms.presentation.dto.InboundItemResponse;
import com.kurly.wms.presentation.dto.InboundOrderResponse;
<<<<<<< HEAD
import com.kurly.wms.presentation.dto.InspectItemRequest;
import com.kurly.wms.presentation.dto.PutAwayConfirmRequest;
import com.kurly.wms.presentation.dto.PutAwayRecommendationRequest;
import com.kurly.wms.presentation.dto.PutAwayRecommendationResponse;
import java.time.LocalDate;
import java.util.EnumSet;
=======
>>>>>>> 40e0939e (feat: 입고 예정(입고 전표) 생성)
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
<<<<<<< HEAD
import org.springframework.data.domain.PageRequest;
=======
>>>>>>> 40e0939e (feat: 입고 예정(입고 전표) 생성)
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InboundOrderService {

<<<<<<< HEAD
    private static final String RECOMMENDATION_REASON = "동선 최적화(aisle/rack/level/bin 오름차순) 기준 최전방 빈 로케이션";

=======
>>>>>>> 40e0939e (feat: 입고 예정(입고 전표) 생성)
    private final InboundOrderRepository inboundOrderRepository;
    private final InboundItemJpaRepository inboundItemJpaRepository;
    private final WarehouseJpaRepository warehouseJpaRepository;
    private final WmsProductJpaRepository wmsProductJpaRepository;
<<<<<<< HEAD
    private final LocationJpaRepository locationJpaRepository;
    private final InventoryJpaRepository inventoryJpaRepository;
    private final StockMovementJpaRepository stockMovementJpaRepository;
    private final OutboxService outboxService;
=======
>>>>>>> 40e0939e (feat: 입고 예정(입고 전표) 생성)

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
<<<<<<< HEAD

    /**
     * 검수 완료 처리 + 버퍼 로케이션 재고 증가 + 적치 작업 지시(StockMovement) 생성 + 입고 완료
     * 이벤트 발행(입고 확정 플로우 1~4단계, Outbox 패턴으로 트랜잭션 커밋과 원자적으로 처리).
     */
    @Transactional
    public InboundItemResponse inspect(InspectItemRequest request) {
        InboundItem item = inboundItemJpaRepository.findWithOptimisticLockById(request.inboundItemId())
                .orElseThrow(() -> new EntityNotFoundException("입고 상세를 찾을 수 없습니다. inboundItemId=" + request.inboundItemId()));

        InboundOrder order = item.getInboundOrder();
        Warehouse warehouse = order.getWarehouse();
        WmsProduct product = item.getProduct();

        Location targetLocation;
        if (request.targetLocationId() != null) {
            targetLocation = locationJpaRepository.findById(request.targetLocationId())
                    .orElseThrow(() -> new EntityNotFoundException("로케이션을 찾을 수 없습니다. targetLocationId=" + request.targetLocationId()));
        } else {
            targetLocation = recommendStorageLocation(warehouse.getId(), product.getStorageType());
        }

        InboundUnit effectiveUnit = request.inboundUnit() != null ? request.inboundUnit() : item.getInboundUnit();
        int totalBaseQuantity = calculateTotalBaseQuantity(product, effectiveUnit, request.inspectQuantity());
        item.inspect(request.inspectQuantity(), totalBaseQuantity, request.lotNo(), request.expiredDate(), targetLocation);

        updateOrderStatus(order);
        Location buffer = receiveIntoBuffer(warehouse, product, request.lotNo(), request.expiredDate(), totalBaseQuantity);
        StockMovement movement = createPutAwayMovement(warehouse, product, item, request.lotNo(), request.expiredDate(),
                buffer, targetLocation, effectiveUnit, request.inspectQuantity(), totalBaseQuantity);
        outboxService.recordInboundCompleted(warehouse.getId(), order.getId(), item.getId(), product.getId(),
                request.lotNo(), request.expiredDate(), totalBaseQuantity);

        return InboundItemResponse.from(item, movement.getId());
    }

    /**
     * 버퍼 → 목표 로케이션 물리 이동 완료 처리(입고 확정 플로우와는 별개로, 검수 이후 현장에서
     * 실제 적치를 마친 뒤 호출하는 API). stockMovementId로 대기 중이던 적치 작업 지시를 직접
     * 찾아 완료 처리하고, 버퍼 재고를 목표 로케이션으로 옮기고, 입고 상세 상태를 PUT_AWAY로 바꾼다.
     */
    @Transactional
    public InboundItemResponse confirmPutAway(PutAwayConfirmRequest request) {
        StockMovement movement = findPendingPutAwayMovement(request.stockMovementId());

        Location targetLocation = locationJpaRepository.findById(request.targetLocationId())
                .orElseThrow(() -> new EntityNotFoundException("로케이션을 찾을 수 없습니다. targetLocationId=" + request.targetLocationId()));

        moveInventory(movement.getWarehouse(), movement.getProduct(), movement.getFromLocation(), targetLocation,
                movement.getLotNo(), movement.getExpiredDate(), movement.getQuantity());

        movement.reassignTarget(targetLocation);
        movement.complete();

        InboundItem item = movement.getInboundItem();
        item.putAway(targetLocation);

        return InboundItemResponse.from(item, movement.getId());
    }

    /**
     * 대기 중인 적치 작업 지시의 목표 로케이션을 다시 추천하고 InboundItem/StockMovement 양쪽에
     * 재배정한다. 기존에 배정돼 있던 로케이션은 그 작업 지시 자신이 이미 점유 중이라 재추천
     * 조건(NOT EXISTS PENDING/IN_PROGRESS)에서 자동으로 제외되므로 항상 다른 로케이션이 나온다.
     */
    @Transactional
    public PutAwayRecommendationResponse recommendPutAway(PutAwayRecommendationRequest request) {
        StockMovement movement = findPendingPutAwayMovement(request.stockMovementId());

        StorageType storageType = movement.getProduct().getStorageType();
        Location newTarget = recommendStorageLocation(movement.getWarehouse().getId(), storageType);

        movement.reassignTarget(newTarget);
        InboundItem item = movement.getInboundItem();
        item.reassignTargetLocation(newTarget);

        return new PutAwayRecommendationResponse(item.getId(), movement.getId(), newTarget.getId(), storageType,
                RECOMMENDATION_REASON, movement.getStatus());
    }

    /**
     * stockMovementId로 대기 중인(PENDING) 적치(PUT_AWAY) 작업 지시를 배타적으로 선점해 찾는다.
     * 같은 stockMovementId로 들어온 동시 요청은 이 행의 락이 풀릴 때까지 대기했다가, 이미 상태가
     * 바뀐 걸 보고 여기서 즉시 실패한다 — moveInventory/item.putAway를 실행하기 전에 걸러진다.
     */
    private StockMovement findPendingPutAwayMovement(Long stockMovementId) {
        StockMovement movement = stockMovementJpaRepository.findWithPessimisticLockById(stockMovementId)
                .orElseThrow(() -> new EntityNotFoundException("적치 작업 지시를 찾을 수 없습니다. stockMovementId=" + stockMovementId));

        if (movement.getMovementType() != MovementType.PUT_AWAY || movement.getStatus() != MovementStatus.PENDING) {
            throw new BusinessException(WmsErrorCode.INVALID_STOCK_MOVEMENT_STATUS,
                    "대기 중인 적치 작업 지시가 아닙니다. stockMovementId=" + movement.getId()
                            + ", movementType=" + movement.getMovementType() + ", status=" + movement.getStatus());
        }
        return movement;
    }

    private Location recommendStorageLocation(Long warehouseId, StorageType storageType) {
        List<Location> candidates = locationJpaRepository.findAvailableLocations(
                warehouseId,
                storageType,
                Zone.STORAGE,
                LocationType.PALLET_RACK,
                LocationStatus.ACTIVE,
                EnumSet.of(MovementStatus.PENDING, MovementStatus.IN_PROGRESS),
                PageRequest.of(0, 1));

        if (candidates.isEmpty()) {
            throw new BusinessException(WmsErrorCode.NO_AVAILABLE_LOCATION);
        }
        return candidates.get(0);
    }

    private int calculateTotalBaseQuantity(WmsProduct product, InboundUnit effectiveUnit, int inspectQuantity) {
        return switch (effectiveUnit) {
            case PALLET -> inspectQuantity * product.getEaPerPallet();
            case CARTON, BOX -> inspectQuantity * product.getBoxUnitQty();
        };
    }

    private MovementUnit toMovementUnit(InboundUnit inboundUnit) {
        return switch (inboundUnit) {
            case PALLET -> MovementUnit.PALLET;
            case CARTON, BOX -> MovementUnit.BOX;
        };
    }

    /** 모든 상세가 검수(이상) 완료면 COMPLETED, 아니면 INSPECTING으로 전표 상태를 갱신한다. */
    private void updateOrderStatus(InboundOrder order) {
        List<InboundItem> items = inboundItemJpaRepository.findByInboundOrderId(order.getId());
        boolean allInspected = items.stream().noneMatch(i -> i.getStatus() == InboundItemStatus.PENDING);
        if (allInspected) {
            order.complete();
        } else {
            order.startInspection();
        }
    }

    /** 검수 완료 수량을 창고의 버퍼(임시 도크) 로케이션 재고에 생성/증가시키고, 그 버퍼 로케이션을 반환한다. */
    private Location receiveIntoBuffer(Warehouse warehouse, WmsProduct product, String lotNo, LocalDate expiredDate, int quantity) {
        Location buffer = locationJpaRepository
                .findFirstByWarehouseIdAndLocationTypeAndStatusOrderByIdAsc(warehouse.getId(), LocationType.BUFFER, LocationStatus.ACTIVE)
                .orElseThrow(() -> new EntityNotFoundException("사용 가능한 버퍼 로케이션이 없습니다. warehouseId=" + warehouse.getId()));

        Inventory inventory = inventoryJpaRepository
                .findByWarehouseIdAndLocationIdAndProductIdAndLotNoAndExpiredDateAndLpnCode(
                        warehouse.getId(), buffer.getId(), product.getId(), lotNo, expiredDate, null)
                .orElseGet(() -> inventoryJpaRepository.save(Inventory.builder()
                        .warehouse(warehouse)
                        .location(buffer)
                        .product(product)
                        .lotNo(lotNo)
                        .expiredDate(expiredDate)
                        .quantity(0)
                        .build()));

        inventory.receive(quantity);
        return buffer;
    }

    /** 버퍼 → 목표 로케이션 물리 이동을 위한 적치 작업 지시를 PENDING 상태로 생성한다. 실제 이동 완료 처리는 put-away/confirm에서 한다. */
    private StockMovement createPutAwayMovement(Warehouse warehouse, WmsProduct product, InboundItem inboundItem, String lotNo, LocalDate expiredDate,
                                                 Location fromLocation, Location toLocation, InboundUnit inboundUnit,
                                                 int unitQuantity, int totalBaseQuantity) {
        return stockMovementJpaRepository.save(StockMovement.builder()
                .warehouse(warehouse)
                .product(product)
                .inboundItem(inboundItem)
                .lotNo(lotNo)
                .expiredDate(expiredDate)
                .fromLocation(fromLocation)
                .toLocation(toLocation)
                .movementUnit(toMovementUnit(inboundUnit))
                .unitQuantity(unitQuantity)
                .quantity(totalBaseQuantity)
                .movementType(MovementType.PUT_AWAY)
                .build());
    }

    /** from 로케이션의 재고를 줄이고 to 로케이션의 재고를 늘려 실물 이동을 반영한다. */
    private void moveInventory(Warehouse warehouse, WmsProduct product, Location from, Location to,
                                String lotNo, LocalDate expiredDate, int quantity) {
        Inventory source = inventoryJpaRepository
                .findByWarehouseIdAndLocationIdAndProductIdAndLotNoAndExpiredDateAndLpnCode(
                        warehouse.getId(), from.getId(), product.getId(), lotNo, expiredDate, null)
                .orElseThrow(() -> new EntityNotFoundException(
                        "이동할 재고를 찾을 수 없습니다. warehouseId=" + warehouse.getId() + ", locationId=" + from.getId()));
        source.remove(quantity);

        Inventory target = inventoryJpaRepository
                .findByWarehouseIdAndLocationIdAndProductIdAndLotNoAndExpiredDateAndLpnCode(
                        warehouse.getId(), to.getId(), product.getId(), lotNo, expiredDate, null)
                .orElseGet(() -> inventoryJpaRepository.save(Inventory.builder()
                        .warehouse(warehouse)
                        .location(to)
                        .product(product)
                        .lotNo(lotNo)
                        .expiredDate(expiredDate)
                        .quantity(0)
                        .build()));
        target.receive(quantity);
    }
=======
>>>>>>> 40e0939e (feat: 입고 예정(입고 전표) 생성)
}
