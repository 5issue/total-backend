package com.kurly.wms.application;

import com.kurly.common.exception.BusinessException;
import com.kurly.common.exception.EntityNotFoundException;
import com.kurly.common.exception.InvalidValueException;
import com.kurly.wms.application.event.PutAwayCompletedEvent;
import com.kurly.wms.application.event.ReplenishmentCompletedEvent;
import com.kurly.wms.domain.enums.StorageType;
import com.kurly.wms.domain.exception.WmsErrorCode;
import com.kurly.wms.infrastructure.entity.InboundItem;
import com.kurly.wms.infrastructure.entity.Inventory;
import com.kurly.wms.infrastructure.entity.Location;
import com.kurly.wms.infrastructure.entity.Location.LocationStatus;
import com.kurly.wms.infrastructure.entity.StockMovement;
import com.kurly.wms.infrastructure.entity.StockMovement.MovementStatus;
import com.kurly.wms.infrastructure.entity.StockMovement.MovementType;
import com.kurly.wms.infrastructure.entity.StockMovement.MovementUnit;
import com.kurly.wms.infrastructure.entity.Warehouse;
import com.kurly.wms.infrastructure.entity.WmsProduct;
import com.kurly.wms.infrastructure.jpa.InventoryJpaRepository;
import com.kurly.wms.infrastructure.jpa.LocationJpaRepository;
import com.kurly.wms.infrastructure.jpa.StockMovementJpaRepository;
import com.kurly.wms.infrastructure.jpa.WarehouseJpaRepository;
import com.kurly.wms.presentation.dto.StockMovementConfirmRequest;
import com.kurly.wms.presentation.dto.StockMovementCreateRequest;
import com.kurly.wms.presentation.dto.StockMovementResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockMovementQueryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final List<MovementStatus> ACTIVE_MOVEMENT_STATUSES = List.of(MovementStatus.PENDING, MovementStatus.IN_PROGRESS);

    private final StockMovementJpaRepository stockMovementJpaRepository;
    private final LocationJpaRepository locationJpaRepository;
    private final InventoryJpaRepository inventoryJpaRepository;
    private final WarehouseJpaRepository warehouseJpaRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional(readOnly = true)
    public List<StockMovementResponse> list(Long warehouseId, MovementType movementType, MovementStatus status, Integer limit) {
        int effectiveLimit = Math.min(limit != null && limit > 0 ? limit : DEFAULT_LIMIT, MAX_LIMIT);

        return stockMovementJpaRepository.search(warehouseId, movementType, status, PageRequest.of(0, effectiveLimit))
                .stream()
                .map(StockMovementResponse::from)
                .toList();
    }

    @Transactional
    public StockMovementResponse create(StockMovementCreateRequest request) {
        if (request.movementType() == MovementType.PUT_AWAY) {
            throw new InvalidValueException("PUT_AWAY는 수동 생성할 수 없으며 입고 검수를 통해서만 생성됩니다.");
        }

        Warehouse warehouse = warehouseJpaRepository.findById(request.warehouseId())
                .orElseThrow(() -> new EntityNotFoundException("창고를 찾을 수 없습니다. warehouseId=" + request.warehouseId()));
        Inventory inventory = inventoryJpaRepository.findWithPessimisticLockById(request.inventoryId())
                .orElseThrow(() -> new EntityNotFoundException("재고를 찾을 수 없습니다. inventoryId=" + request.inventoryId()));
        Location targetLocation = locationJpaRepository.findWithPessimisticLockById(request.targetLocationId())
                .orElseThrow(() -> new EntityNotFoundException("로케이션을 찾을 수 없습니다. targetLocationId=" + request.targetLocationId()));

        if (!Objects.equals(inventory.getLocation().getId(), request.fromLocationId())) {
            throw new InvalidValueException("입력된 fromLocationId가 재고의 실제 위치와 일치하지 않습니다. inventoryId=" + request.inventoryId() + ", fromLocationId=" + request.fromLocationId());
        }
        if (!Objects.equals(inventory.getProduct().getId(), request.productId())) {
            throw new InvalidValueException("재고의 상품과 요청된 productId가 일치하지 않습니다. inventoryId=" + request.inventoryId() + ", productId=" + request.productId());
        }
        if (!Objects.equals(inventory.getWarehouse().getId(), warehouse.getId())) {
            throw new InvalidValueException("재고가 올바른 창고에 속하지 않습니다. inventoryId=" + request.inventoryId() + ", warehouseId=" + warehouse.getId());
        }
        validateTargetLocation(warehouse, inventory.getLocation().getStorageType(), request.fromLocationId(), targetLocation);
        validateTargetLocationAvailable(targetLocation);

        int totalBaseQuantity = calculateTotalBaseQuantity(inventory.getProduct(), request.movementUnit(), request.unitQuantity());
        int availableQuantity = inventory.getQuantity() - inventory.getReservedQuantity();
        if (totalBaseQuantity <= 0 || availableQuantity < totalBaseQuantity) {
            throw new InvalidValueException("이동 가능한 재고 수량이 부족합니다. inventoryId=" + request.inventoryId() + ", availableQuantity=" + availableQuantity + ", requestedQuantity=" + totalBaseQuantity);
        }

        inventory.reserve(totalBaseQuantity);

        StockMovement movement = StockMovement.builder()
                .warehouse(warehouse)
                .product(inventory.getProduct())
                .lpnCode(inventory.getLpnCode())
                .lotNo(inventory.getLotNo())
                .expiredDate(inventory.getExpiredDate())
                .fromLocation(inventory.getLocation())
                .toLocation(targetLocation)
                .movementUnit(request.movementUnit())
                .unitQuantity(request.unitQuantity())
                .quantity(totalBaseQuantity)
                .movementType(request.movementType())
                .build();
        StockMovement savedMovement = stockMovementJpaRepository.save(movement);
        return StockMovementResponse.from(savedMovement);
    }

    @Transactional
    public StockMovementResponse confirm(StockMovementConfirmRequest request) {
        StockMovement movement = findPendingMovement(request.stockMovementId());

        Location targetLocation = locationJpaRepository.findWithPessimisticLockById(request.targetLocationId())
                .orElseThrow(() -> new EntityNotFoundException("로케이션을 찾을 수 없습니다. targetLocationId=" + request.targetLocationId()));

        validateTargetLocation(movement.getWarehouse(), movement.getProduct().getStorageType(),
                movement.getFromLocation().getId(), targetLocation);
        if (!Objects.equals(movement.getToLocation().getId(), targetLocation.getId())) {
            validateTargetLocationAvailable(targetLocation);
        }

        moveInventory(movement.getWarehouse(), movement.getProduct(), movement.getFromLocation(), targetLocation,
                movement.getLotNo(), movement.getExpiredDate(), movement.getQuantity());

        movement.reassignTarget(targetLocation);
        movement.complete();

        if (movement.getMovementType() == MovementType.PUT_AWAY) {
            InboundItem item = movement.getInboundItem();
            item.putAway(targetLocation);
            applicationEventPublisher.publishEvent(
                    new PutAwayCompletedEvent(movement.getWarehouse().getId(), movement.getProduct().getId()));
        } else if (movement.getMovementType() == MovementType.REPLENISHMENT) {
            applicationEventPublisher.publishEvent(new ReplenishmentCompletedEvent(
                    movement.getWarehouse().getId(), movement.getProduct().getId(),
                    movement.getToLocation().getId(), movement.getLotNo(), movement.getExpiredDate(), movement.getQuantity()));
        }
        return StockMovementResponse.from(movement);
    }


    private void validateTargetLocation(Warehouse warehouse, StorageType requiredStorageType, Long fromLocationId, Location targetLocation) {
        if (!Objects.equals(targetLocation.getWarehouse().getId(), warehouse.getId())) {
            throw new InvalidValueException("목적지 로케이션이 올바른 창고에 속하지 않습니다. targetLocationId=" + targetLocation.getId() + ", warehouseId=" + warehouse.getId());
        }
        if (targetLocation.getStatus() != LocationStatus.ACTIVE) {
            throw new InvalidValueException("목적지 로케이션을 사용할 수 없는 상태입니다. targetLocationId=" + targetLocation.getId() + ", status=" + targetLocation.getStatus());
        }
        if (requiredStorageType != targetLocation.getStorageType()) {
            throw new InvalidValueException("보관 유형이 일치하지 않아 이동할 수 없습니다. targetLocationId=" + targetLocation.getId()
                    + ", required=" + requiredStorageType + ", actual=" + targetLocation.getStorageType());
        }
        if (Objects.equals(fromLocationId, targetLocation.getId())) {
            throw new InvalidValueException("출발지 로케이션과 목적지 로케이션이 동일합니다. locationId=" + targetLocation.getId());
        }
    }

    /** 목적지 로케이션이 완전히 비어 있고, 다른 PENDING/IN_PROGRESS 작업 지시의 목적지로 이미 잡혀 있지 않은지 검증한다. */
    private void validateTargetLocationAvailable(Location targetLocation) {
        boolean hasExistingStock = inventoryJpaRepository.existsByLocationIdAndQuantityGreaterThan(targetLocation.getId(), 0);
        boolean hasAlreadyTargeted = stockMovementJpaRepository.existsByToLocationIdAndStatusIn(targetLocation.getId(), ACTIVE_MOVEMENT_STATUSES);
        if (hasExistingStock || hasAlreadyTargeted) {
            throw new InvalidValueException("목적지 로케이션에 이미 재고가 있거나 다른 이동 작업이 진행 중입니다. targetLocationId=" + targetLocation.getId());
        }
    }

    private StockMovement findPendingMovement(Long stockMovementId) {
        StockMovement movement = stockMovementJpaRepository.findWithPessimisticLockById(stockMovementId)
                .orElseThrow(() -> new EntityNotFoundException("작업 지시를 찾을 수 없습니다. stockMovementId=" + stockMovementId));

        if (movement.getStatus() != MovementStatus.PENDING) {
            throw new BusinessException(WmsErrorCode.INVALID_STOCK_MOVEMENT_STATUS,
                    "대기 중인 작업 지시가 아닙니다. stockMovementId=" + movement.getId() + ", status=" + movement.getStatus());
        }
        return movement;
    }

    private void moveInventory(Warehouse warehouse, WmsProduct product, Location from, Location to,
                               String lotNo, LocalDate expiredDate, int quantity) {
        Inventory source = inventoryJpaRepository
                .findByWarehouseIdAndLocationIdAndProductIdAndLotNoAndExpiredDateAndLpnCode(
                        warehouse.getId(), from.getId(), product.getId(), lotNo, expiredDate, null)
                .orElseThrow(() -> new EntityNotFoundException(
                        "이동할 재고를 찾을 수 없습니다. warehouseId=" + warehouse.getId() + ", locationId=" + from.getId()));

        source.release(quantity);
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

    private int calculateTotalBaseQuantity(WmsProduct product, MovementUnit movementUnit, int quantity) {
        try {
            return switch (movementUnit) {
                case PALLET -> Math.multiplyExact(quantity, product.getEaPerPallet());
                case BOX -> Math.multiplyExact(quantity, product.getBoxUnitQty());
                case EA -> quantity;
            };
        } catch (ArithmeticException e) {
            throw new InvalidValueException("수량 환산 결과가 허용 가능한 정수 범위를 초과했습니다.");
        }
    }
}
