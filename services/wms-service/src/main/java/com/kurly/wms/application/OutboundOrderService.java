package com.kurly.wms.application;

import com.kurly.common.exception.EntityNotFoundException;
import com.kurly.wms.infrastructure.entity.Inventory;
import com.kurly.wms.infrastructure.entity.Location;
import com.kurly.wms.infrastructure.entity.Location.Zone;
import com.kurly.wms.infrastructure.entity.OutboundItem;
import com.kurly.wms.infrastructure.entity.OutboundItem.OutboundItemStatus;
import com.kurly.wms.infrastructure.entity.OutboundOrder;
import com.kurly.wms.infrastructure.entity.OutboundOrder.OutboundOrderStatus;
import com.kurly.wms.infrastructure.entity.StockMovement;
import com.kurly.wms.infrastructure.entity.StockMovement.MovementType;
import com.kurly.wms.infrastructure.entity.StockMovement.MovementUnit;
import com.kurly.wms.infrastructure.entity.Warehouse;
import com.kurly.wms.infrastructure.entity.WmsProduct;
import com.kurly.wms.infrastructure.jpa.InventoryJpaRepository;
import com.kurly.wms.infrastructure.jpa.LocationJpaRepository;
import com.kurly.wms.infrastructure.jpa.OutboundItemJpaRepository;
import com.kurly.wms.infrastructure.jpa.OutboundOrderJpaRepository;
import com.kurly.wms.infrastructure.jpa.StockMovementJpaRepository;
import com.kurly.wms.infrastructure.jpa.WarehouseJpaRepository;
import com.kurly.wms.infrastructure.jpa.WmsProductJpaRepository;
import com.kurly.wms.infrastructure.messaging.dto.OrderEvent;
import com.kurly.wms.infrastructure.messaging.dto.OrderEvent.OrderEventItem;
import com.kurly.wms.infrastructure.scheduler.OutboundFailureProperties;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboundOrderService {

    private final OutboundOrderJpaRepository outboundOrderJpaRepository;
    private final OutboundItemJpaRepository outboundItemJpaRepository;
    private final WarehouseJpaRepository warehouseJpaRepository;
    private final WmsProductJpaRepository wmsProductJpaRepository;
    private final InventoryJpaRepository inventoryJpaRepository;
    private final StockMovementJpaRepository stockMovementJpaRepository;
    private final LocationJpaRepository locationJpaRepository;
    private final OutboundFailureProperties outboundFailureProperties;

    /**
     * 결제 완료 이벤트({@code order.inventory.confirm})를 받아 출고 전표(OutboundOrder)를
     * 생성하고, 품목별로 피킹존(PICKING) 재고를 FEFO(유통기한 오름차순)로 하드 할당한다.
     * 피킹존 재고가 부족한 만큼은 보관존(STORAGE)에서 피킹존으로의 보충 지시(StockMovement,
     * REPLENISHMENT)를 트리거해 PENDING_REPLENISHMENT로, 그마저도 안 되는 만큼은
     * UNALLOCATED인 OutboundItem으로 남긴다. OutboundOrder의 상태는 생성된 OutboundItem들의
     * 실제 상태로부터 그대로 도출한다 — 하나라도 ALLOCATED가 아니면 전표도 PENDING_REPLENISHMENT,
     * 전부 ALLOCATED면 전표도 ALLOCATED(생성자 기본값)로 남긴다.
     */
    @Transactional
    public void createFromOrderEvent(OrderEvent event) {
        if (outboundOrderJpaRepository.findByOrderId(event.orderId()).isPresent()) {
            log.info("이미 생성된 출고 지시라 건너뛴다. orderId={}", event.orderId());
            return;
        }

        Warehouse warehouse = warehouseJpaRepository.findById(event.warehouseId())
                .orElseThrow(() -> new EntityNotFoundException("창고를 찾을 수 없습니다. warehouseId=" + event.warehouseId()));

        List<Long> productIds = event.items().stream()
                .map(OrderEventItem::productId)
                .distinct()
                .toList();
        Map<Long, WmsProduct> wmsProductMap = wmsProductJpaRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(WmsProduct::getId, product -> product));
        if (wmsProductMap.size() != productIds.size()) {
            throw new EntityNotFoundException("상품을 찾을 수 없습니다. productIds=" + productIds);
        }

        OutboundOrder outboundOrder = outboundOrderJpaRepository.save(OutboundOrder.builder()
                .orderId(event.orderId())
                .warehouse(warehouse)
                .build());

        List<OutboundItem> outboundItems = new ArrayList<>();
        for (OrderEventItem item : event.items()) {
            WmsProduct product = wmsProductMap.get(item.productId());
            allocate(outboundOrder, warehouse, product, item.quantity(), outboundItems);
        }
        outboundItemJpaRepository.saveAll(outboundItems);

        boolean anyUnallocated = outboundItems.stream()
                .anyMatch(outboundItem -> outboundItem.getStatus() != OutboundItemStatus.ALLOCATED);
        if (anyUnallocated) {
            outboundOrder.hold();
        }

        log.info("출고 지시 생성 완료. orderId={}, outboundOrderId={}, status={}, 품목수={}",
                event.orderId(), outboundOrder.getId(), outboundOrder.getStatus(), outboundItems.size());
    }

    /**
     * 피킹존 가용 재고를 FEFO 순서로 소진하며 필요하면 여러 LOT/로케이션에 걸쳐 분할 할당한다.
     * 끝까지 못 채운 나머지는 보관존 보충을 시도해, 보충 예약이 걸린 만큼은
     * PENDING_REPLENISHMENT, 그마저도 안 되는 만큼은 UNALLOCATED OutboundItem으로 남긴다.
     */
    private void allocate(OutboundOrder outboundOrder, Warehouse warehouse, WmsProduct product,
                           int orderedQuantity, List<OutboundItem> outboundItems) {
        List<Inventory> candidates = inventoryJpaRepository.findAllocatableByFefo(warehouse.getId(), product.getId(), Zone.PICKING);

        int remaining = orderedQuantity;
        for (Inventory inventory : candidates) {
            if (remaining <= 0) {
                break;
            }
            int allocatedQuantity = Math.min(inventory.getAvailableQuantity(), remaining);
            inventory.reserve(allocatedQuantity);
            outboundItems.add(OutboundItem.builder()
                    .outboundOrder(outboundOrder)
                    .product(product)
                    .lotNo(inventory.getLotNo())
                    .expiredDate(inventory.getExpiredDate())
                    .location(inventory.getLocation())
                    .orderedQuantity(allocatedQuantity)
                    .build());
            remaining -= allocatedQuantity;
        }

        if (remaining <= 0) {
            return;
        }

        int reserved = triggerReplenishment(warehouse, product, remaining);
        if (reserved > 0) {
            outboundItems.add(OutboundItem.builder()
                    .outboundOrder(outboundOrder)
                    .product(product)
                    .orderedQuantity(reserved)
                    .pendingStatus(OutboundItemStatus.PENDING_REPLENISHMENT)
                    .build());
        }

        int stillUnreserved = remaining - reserved;
        if (stillUnreserved > 0) {
            outboundItems.add(OutboundItem.builder()
                    .outboundOrder(outboundOrder)
                    .product(product)
                    .orderedQuantity(stillUnreserved)
                    .pendingStatus(OutboundItemStatus.UNALLOCATED)
                    .build());
        }
    }

    /**
     * 보관존(STORAGE) 재고를 FEFO 순서로 피킹존까지 옮기는 보충 지시를 만든다. 목적지는 이
     * 상품이 이미 배치돼 있는(그리고 보관 유형이 상품과 일치하는) 피킹존 로케이션을 재사용한다
     * — 피킹존 로케이션 배정 전략(고정 슬롯 vs 동적 배정)이 아직 확정되지 않아, 한 번도 배치된
     * 적 없는 상품은 보충 지시를 만들지 못한다. 반환값은 실제로 보충 지시(예약)를 걸 수 있었던
     * 수량이다 — shortage와의 차이만큼은 호출부가 UNALLOCATED으로 남긴다.
     */
    private int triggerReplenishment(Warehouse warehouse, WmsProduct product, int shortage) {
        Optional<Location> pickingLocation = inventoryJpaRepository
                .findFirstByWarehouseIdAndProductIdAndLocation_ZoneAndLocation_StorageType(
                        warehouse.getId(), product.getId(), Zone.PICKING, product.getStorageType())
                .map(Inventory::getLocation);
        if (pickingLocation.isEmpty()) {
            log.warn("피킹존에 이 상품(보관 유형 일치) 재고가 한 번도 없어 보충 지시를 생성하지 못했다(피킹 로케이션 배정 전략 미확정). "
                    + "warehouseId={}, productId={}, storageType={}, 부족분={}",
                    warehouse.getId(), product.getId(), product.getStorageType(), shortage);
            return 0;
        }

        List<Inventory> storageCandidates = inventoryJpaRepository.findAllocatableByFefo(warehouse.getId(), product.getId(), Zone.STORAGE);
        int remaining = shortage;
        for (Inventory source : storageCandidates) {
            if (remaining <= 0) {
                break;
            }
            int moveQuantity = Math.min(source.getAvailableQuantity(), remaining);
            source.reserve(moveQuantity);
            stockMovementJpaRepository.save(StockMovement.builder()
                    .warehouse(warehouse)
                    .product(product)
                    .lotNo(source.getLotNo())
                    .expiredDate(source.getExpiredDate())
                    .fromLocation(source.getLocation())
                    .toLocation(pickingLocation.get())
                    .movementUnit(MovementUnit.EA)
                    .unitQuantity(moveQuantity)
                    .quantity(moveQuantity)
                    .movementType(MovementType.REPLENISHMENT)
                    .build());
            remaining -= moveQuantity;
        }

        int reserved = shortage - remaining;
        if (remaining > 0) {
            log.warn("보관존 재고도 부족해 보충 지시를 일부만 생성했다. warehouseId={}, productId={}, 예약={}, 못 채운 부족분={}",
                    warehouse.getId(), product.getId(), reserved, remaining);
        }
        return reserved;
    }

    /**
     * 장시간(기본 2시간) 재고를 전혀 확보하지 못한(UNALLOCATED) 출고 전표를 최종 실패
     * 처리한다. 보충 지시가 이미 걸려 있는(PENDING_REPLENISHMENT) 전표는 대상이 아니다 — 그건
     * 물리 이동만 기다리면 되는 정상 진행 중인 상태라서다. 스케줄러가 주기적으로 호출한다.
     *
     * <p>전표가 실패해도 그 안의 다른 품목이 이미 ALLOCATED/PENDING_REPLENISHMENT였을 수 있다 —
     * 이대로 두면 ① ALLOCATED 품목이 쥐고 있던 피킹존 재고 예약(reserved_quantity)이 영원히
     * 안 풀리고, ② PENDING_REPLENISHMENT 품목은 여전히 대기 상태로 남아 나중에
     * {@code finalizeReplenishment()}가 이미 죽은 전표에 실제 재고를 배정해버릴 수 있다. 그래서
     * 상태와 무관하게 전표의 모든 품목을 훑어 ALLOCATED는 예약을 해제하고, 전부 FAILED로
     * 전환한다. PENDING_REPLENISHMENT 품목이 예약해둔 보관존 재고는 별도로 되돌리지 않는다 —
     * 그 예약은 이미 생성된 REPLENISHMENT StockMovement에 묶여 있고, 그 이동이 실제로 완료되면
     * {@code moveInventory()}가 보관존 쪽 예약을 알아서 해제·차감하기 때문이다(이 전표가 실패해도
     * 물리 이동 자체는 그대로 진행되고, 도착한 재고는 다음으로 대기 중인 다른 전표에 자연스럽게
     * 재배정된다 — FAILED 품목은 더 이상 PENDING_REPLENISHMENT 조회에 잡히지 않으므로).
     */
    @Transactional
    public void failStuckOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minus(outboundFailureProperties.allocationTimeout());
        List<OutboundOrder> stuckOrders = outboundOrderJpaRepository.findTimedOutUnallocated(
                cutoff, OutboundOrderStatus.FAILED, OutboundItemStatus.UNALLOCATED);

        for (OutboundOrder order : stuckOrders) {
            for (OutboundItem item : outboundItemJpaRepository.findByOutboundOrderId(order.getId())) {
                if (item.getStatus() == OutboundItemStatus.ALLOCATED) {
                    releaseAllocatedReservation(order.getWarehouse(), item);
                }
                item.fail();
            }
            order.fail();
            log.warn("장시간 재고를 확보하지 못해 출고 전표를 실패 처리한다(할당됐던 재고 예약도 해제). outboundOrderId={}, orderId={}",
                    order.getId(), order.getOrderId());
        }

        if (!stuckOrders.isEmpty()) {
            log.info("재고 확보 실패로 전환된 출고 전표 수={}", stuckOrders.size());
        }
    }

    /** ALLOCATED 품목이 피킹존 재고에 걸어둔 reserved_quantity를 되돌려, 다른 대기 중인 주문이 다시 쓸 수 있게 한다. */
    private void releaseAllocatedReservation(Warehouse warehouse, OutboundItem item) {
        Optional<Inventory> inventory = inventoryJpaRepository.findByWarehouseIdAndLocationIdAndProductIdAndLotNoAndExpiredDateAndLpnCode(
                warehouse.getId(), item.getLocation().getId(), item.getProduct().getId(),
                item.getLotNo(), item.getExpiredDate(), null);
        if (inventory.isEmpty()) {
            log.warn("실패 처리 중 할당 해제 대상 재고를 찾지 못했다. outboundItemId={}, locationId={}, productId={}",
                    item.getId(), item.getLocation().getId(), item.getProduct().getId());
            return;
        }
        inventory.get().release(item.getOrderedQuantity());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryUnallocated(Long warehouseId, Long productId) {
        List<OutboundItem> unallocatedItems = outboundItemJpaRepository
                .findByWarehouseIdAndProductIdAndStatusOrderByOutboundOrderCreatedAtAsc(
                        warehouseId, productId, OutboundItemStatus.UNALLOCATED);
        if (unallocatedItems.isEmpty()) {
            return;
        }

        Warehouse warehouse = warehouseJpaRepository.findById(warehouseId)
                .orElseThrow(() -> new EntityNotFoundException("창고를 찾을 수 없습니다. warehouseId=" + warehouseId));
        WmsProduct product = wmsProductJpaRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("상품을 찾을 수 없습니다. productId=" + productId));

        List<OutboundItem> newlyReserved = new ArrayList<>();
        for (OutboundItem item : unallocatedItems) {
            int reserved = triggerReplenishment(warehouse, product, item.getOrderedQuantity());
            if (reserved <= 0) {
                continue;
            }
            if (reserved >= item.getOrderedQuantity()) {
                item.markReplenishmentReserved();
            } else {
                item.reduceOrderedQuantity(reserved);
                newlyReserved.add(OutboundItem.builder()
                        .outboundOrder(item.getOutboundOrder())
                        .product(product)
                        .orderedQuantity(reserved)
                        .pendingStatus(OutboundItemStatus.PENDING_REPLENISHMENT)
                        .build());
            }
        }
        outboundItemJpaRepository.saveAll(newlyReserved);
    }

    /**
     * REPLENISHMENT 이동이 실제로 완료돼 피킹존에 도착한 재고를, 대기 중인 PENDING_REPLENISHMENT
     * 품목에 먼저 들어온 전표부터(FIFO) 나눠 최종 할당한다. 이 이동으로 옮겨진 수량을 넘어서는
     * 대기 품목은 다음 REPLENISHMENT 완료를 기다리며 그대로 PENDING_REPLENISHMENT로 남는다.
     * 어떤 전표가 이걸로 모든 품목이 ALLOCATED가 됐으면 전표도 ALLOCATED로 되돌린다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeReplenishment(Long warehouseId, Long productId, Long locationId, String lotNo,
                                       LocalDate expiredDate, int quantity) {
        List<OutboundItem> pendingItems = outboundItemJpaRepository
                .findByWarehouseIdAndProductIdAndStatusOrderByOutboundOrderCreatedAtAsc(
                        warehouseId, productId, OutboundItemStatus.PENDING_REPLENISHMENT);
        if (pendingItems.isEmpty()) {
            return;
        }

        Location location = locationJpaRepository.findById(locationId)
                .orElseThrow(() -> new EntityNotFoundException("로케이션을 찾을 수 없습니다. locationId=" + locationId));

        List<OutboundItem> newlyAllocated = new ArrayList<>();
        List<OutboundOrder> touchedOrders = new ArrayList<>();
        int remaining = quantity;
        for (OutboundItem item : pendingItems) {
            if (remaining <= 0) {
                break;
            }
            int allocatedQuantity = Math.min(item.getOrderedQuantity(), remaining);
            if (allocatedQuantity >= item.getOrderedQuantity()) {
                item.allocate(location, lotNo, expiredDate);
            } else {
                item.reduceOrderedQuantity(allocatedQuantity);
                newlyAllocated.add(OutboundItem.builder()
                        .outboundOrder(item.getOutboundOrder())
                        .product(item.getProduct())
                        .lotNo(lotNo)
                        .expiredDate(expiredDate)
                        .location(location)
                        .orderedQuantity(allocatedQuantity)
                        .build());
            }
            touchedOrders.add(item.getOutboundOrder());
            remaining -= allocatedQuantity;
        }
        outboundItemJpaRepository.saveAll(newlyAllocated);

        touchedOrders.stream().distinct().forEach(order -> {
            if (!outboundItemJpaRepository.existsByOutboundOrderIdAndStatusNot(order.getId(), OutboundItemStatus.ALLOCATED)) {
                order.allocate();
            }
        });
    }
}
