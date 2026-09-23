package com.kurly.order.application;

import com.kurly.common.exception.BusinessException;
import com.kurly.common.exception.GlobalErrorCode;
import com.kurly.common.security.AuthenticatedPrincipal;
import com.kurly.order.domain.cart.Cart;
import com.kurly.order.domain.cart.CartItem;
import com.kurly.order.domain.cart.CartItemRepository;
import com.kurly.order.domain.cart.CartRepository;
import com.kurly.order.domain.claim.*;
import com.kurly.order.domain.common.OrderErrorCode;
import com.kurly.order.domain.common.StorageType;
import com.kurly.order.domain.order.*;
import com.kurly.order.infrastructure.dto.AddressResponse;
import com.kurly.order.infrastructure.dto.CancelEligibilityResponse;
import com.kurly.order.infrastructure.dto.CartProductInfo;
import com.kurly.order.infrastructure.dto.CheckoutInventoryResponseDto;
import com.kurly.order.infrastructure.messaging.*;
import com.kurly.order.presentation.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class OrderService {

    private static final Duration PAYMENT_TIMEOUT = Duration.ofMinutes(5);

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderClaimRepository orderClaimRepository;
    private final OrderDeliveryInfoRepository orderDeliveryInfoRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderExternalService externalService;
    private final CartExternalService cartExternalService;

    @Value("${services.storage.refund-attachment-bucket:refund-attachments}")
    private String refundAttachmentBucket;

    @Transactional
    public CheckoutResponseDto checkout(AuthenticatedPrincipal me, CheckoutRequestDto request) {
        Long memberId = me.userId();
        if (request.cartItemIds() == null || request.cartItemIds().isEmpty()) {
            throw new BusinessException(OrderErrorCode.ORD_INVALID_CART_ITEMS);
        }

        Cart cart = cartRepository.findByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORD_INVALID_CART_ITEMS));

        if (cart.getAddressId() == null) {
            throw new BusinessException(OrderErrorCode.ORD_NOT_FOUND_ADDRESS);
        }

        List<CartItem> selectedItems = cart.getItems().stream()
                .filter(item -> request.cartItemIds().contains(item.getId()))
                .toList();

        if (selectedItems.isEmpty() || selectedItems.size() != request.cartItemIds().stream().distinct().count()) {
            throw new BusinessException(OrderErrorCode.ORD_INVALID_CART_ITEMS);
        }

        AddressResponse address = externalService.getAddress(memberId, cart.getAddressId());
        if (address == null || address.addressId() == null
            || address.recipientName() == null || address.recipientName().isBlank()
            || address.recipientPhone() == null || address.recipientPhone().isBlank()
            || address.address() == null || address.address().isBlank()) {
            throw new BusinessException(OrderErrorCode.ORD_NOT_FOUND_ADDRESS);
        }

        List<Long> productIds = selectedItems.stream().map(CartItem::getProductId).toList();
        Map<Long, CartProductInfo> productMap;
        try {
            List<CartProductInfo> productInfos = cartExternalService.getProducts(productIds);
            if (productInfos == null) {
                throw new IllegalStateException("상품 서비스의 상품 응답이 비어 있습니다.");
            }
            productMap = productInfos.stream()
                    .collect(Collectors.toMap(CartProductInfo::productId, Function.identity()));
        } catch (RestClientException | IllegalStateException e) {
            throw new BusinessException(OrderErrorCode.ORD_INCOMPLETE_PRODUCT_RESPONSE);
        }

        if (!productMap.keySet().containsAll(productIds)) {
            throw new BusinessException(OrderErrorCode.ORD_INCOMPLETE_PRODUCT_RESPONSE);
        }

        List<OrderItem> orderItems = selectedItems.stream()
                .map(item -> {
                    CartProductInfo info = productMap.get(item.getProductId());
                    return OrderItem.create(
                            item.getProductId(),
                            0L,
                            0L,
                            info.name(),
                            null,
                            item.getStorageType(),
                            item.getQuantity(),
                            info.salePrice()
                    );
                })
                .toList();

        orderRepository.findActiveCheckoutForUpdate(memberId).ifPresent(existing -> {
            if (existing.getInventoryReservationToken() != null &&
                existing.getInventoryReservedUntil() != null &&
                existing.getInventoryReservedUntil().isAfter(LocalDateTime.now())) {
                externalService.releaseInventory(existing.getInventoryReservationToken());
            }
            existing.markExpired();
        });

        UUID reservationToken = UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(15);

        try {
            externalService.holdInventory(reservationToken, selectedItems);
        } catch (HttpClientErrorException.Conflict e) {
            throw new BusinessException(OrderErrorCode.ORD_INSUFFICIENT_STOCK);
        } catch (RestClientException | IllegalStateException e) {
            throw new BusinessException(OrderErrorCode.ORD_INCOMPLETE_PRODUCT_RESPONSE, "상품 재고 서비스 통신에 실패했습니다.");
        }

        String orderNo = "O" + reservationToken.toString().replace("-", "").substring(0, 20).toUpperCase();

        Order order = orderRepository.save(Order.createCheckout(
                orderNo,
                memberId,
                reservationToken,
                expiresAt,
                0L, //  hold.shippingFee(),
                orderItems
        ));

        OrderDeliveryInfo deliveryInfo = OrderDeliveryInfo.createSnapshot(
                order,
                cart.getRegionId(),
                address.addressId(),
                address.recipientName(),
                address.recipientPhone(),
                "11111", // address.zipCode(),
                address.address(),
                address.detailAddress(),
                address.addressName(),
                null, null, null, null
        );
        orderDeliveryInfoRepository.save(deliveryInfo);

        return CheckoutResponseDto.from(order);
    }

    public OrderPageResponseDto getAll(AuthenticatedPrincipal me, String range, String productName, int page, int size) {
        int months = switch (range) {
            case "3M" -> 3;
            case "6M" -> 6;
            case "1Y" -> 12;
            case "3Y" -> 36;
            default -> throw new BusinessException(OrderErrorCode.ORD_INVALID_RANGE);
        };

        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE, "페이지 번호 및 크기가 올바르지 않습니다.");
        }
        if (productName != null && productName.length() > 100) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE, "검색어는 최대 100자까지 입력 가능합니다.");
        }

        var orders = orderRepository.findOrders(
                me.userId(),
                LocalDateTime.now().minusMonths(months),
                productName == null || productName.isBlank() ? null : productName,
                PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        return OrderPageResponseDto.from(orders, page, size);
    }

    public OrderDetailResponseDto getById(AuthenticatedPrincipal me, Long orderId) {
        Order order = getOwnedOrder(me, orderId);

        if (order.getStatus() == OrderStatus.CHECKOUT_CREATED || order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(OrderErrorCode.ORD_NOT_FOUND_ORDER);
        }

        return OrderDetailResponseDto.from(order);
    }

    public ClaimHistoryPageResponseDto getClaimHistories(AuthenticatedPrincipal me, String requestType,
                                                         String requestStatus, int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE, "페이지 번호 및 크기가 올바르지 않습니다.");
        }

        ClaimType type = parseClaimType(requestType);
        ClaimStatus status = parseClaimStatus(requestStatus);

        var result = orderClaimRepository.findClaims(
                me.userId(),
                type,
                status,
                PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "requestedAt"))
        );

        return ClaimHistoryPageResponseDto.from(result, page, size);
    }

    public ReturnPreviewResponseDto getReturnPreview(AuthenticatedPrincipal me, Long orderId) {
        Order order = getOwnedOrder(me, orderId);
        ensureNoClaim(orderId);

        if (order.getDeliveryStatus() != DeliveryStatus.DELIVERED || order.getDeliveredAt() == null) {
            throw new BusinessException(OrderErrorCode.ORD_INVALID_DELIVERY_STATUS);
        }

        boolean hasColdItem = hasColdItem(order);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime defectDeadline = hasColdItem ? order.getDeliveredAt().plusHours(48) : order.getDeliveredAt().plusMonths(3);

        if (now.isAfter(defectDeadline)) {
            throw new BusinessException(OrderErrorCode.ORD_EXPIRED_RETURN_PERIOD);
        }

        var reasons = new java.util.ArrayList<ReturnPreviewResponseDto.ReasonOption>();
        if (!hasColdItem && !now.isAfter(order.getDeliveredAt().plusDays(7))) {
            reasons.add(new ReturnPreviewResponseDto.ReasonOption("RTN01", "단순 변심", false));
        }

        reasons.addAll(Arrays.stream(ReturnReason.values())
                .filter(reason -> reason != ReturnReason.RTN01)
                .map(reason -> new ReturnPreviewResponseDto.ReasonOption(
                        reason.name(), reason.description(), reason.evidenceRequired()))
                .toList());

        StorageType policy = hasColdItem
                ? order.getItems().stream()
                  .map(OrderItem::getStorageType)
                  .filter(type -> type != StorageType.ROOM_TEMPERATURE)
                  .findFirst()
                  .orElse(StorageType.REFRIGERATED)
                : StorageType.ROOM_TEMPERATURE;

        return new ReturnPreviewResponseDto(
                orderId,
                true,
                policy,
                reasons,
                new ReturnPreviewResponseDto.RefundPreview(order.getPaymentAmount(), 0L, order.getPaymentAmount()),
                new ReturnPreviewResponseDto.ReturnPolicy(
                        !hasColdItem,
                        hasColdItem ? "상품 사진 확인 후 자체 폐기 여부가 결정됩니다." : "회수 및 검수 후 환불됩니다."
                )
        );
    }

    @Transactional
    public PlaceOrderResponseDto placeOrder(AuthenticatedPrincipal me, Long orderId) {
        Order order = getOwnedOrderForUpdate(me, orderId);

        if (order.getStatus() == OrderStatus.PAID) {
            throw new BusinessException(OrderErrorCode.ORD_CONFLICT_ALREADY_PROCESSED, order.getStatus().name());
        }
        if (order.getStatus() != OrderStatus.CHECKOUT_CREATED) {
            throw new BusinessException(OrderErrorCode.ORD_INVALID_STATUS);
        }

        order.markPaymentPending(LocalDateTime.now().plus(PAYMENT_TIMEOUT));

        List<Long> orderProductIds = order.getItems().stream()
                .map(OrderItem::getProductId)
                .toList();

        Cart cart = cartRepository.findByMemberIdForUpdate(me.userId())
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORD_INVALID_CART_ITEMS));

        cart.getItems().removeIf(item -> orderProductIds.contains(item.getProductId()));

        return PlaceOrderResponseDto.from(order);
    }

    public InternalOrderResponseDto getForPayment(Long orderId) {
        Order order = getOrder(orderId);
        long remainingSeconds = order.getInventoryReservedUntil() == null ? 0
                : Math.max(0, Duration.between(LocalDateTime.now(), order.getInventoryReservedUntil()).toSeconds());
        return InternalOrderResponseDto.from(order, remainingSeconds);
    }

    public InternalOrderItemsResponseDto getItems(Long orderId) {
        return InternalOrderItemsResponseDto.from(getOrder(orderId));
    }

    @Transactional
    public CompletePayResponseDto completePay(Long orderId, CompletePayRequestDto request) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORD_NOT_FOUND_ORDER));

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(OrderErrorCode.ORD_CONFLICT_ALREADY_PROCESSED, order.getStatus().name());
        }
        if (!order.getPaymentAmount().equals(request.paymentAmount())) {
            throw new BusinessException(OrderErrorCode.ORD_INVALID_PAYMENT_AMOUNT);
        }
        if (order.getInventoryReservedUntil().isBefore(request.paidAt())) {
            throw new BusinessException(OrderErrorCode.ORD_EXPIRED_PAYMENT_TIMEOUT);
        }

        order.markPaid(request.paymentId(), request.paidAt());

        OrderDeliveryInfo deliveryInfo = orderDeliveryInfoRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORD_NOT_FOUND_ORDER));

        eventPublisher.publishEvent(OrderPaymentCompletedEvent.of(order, deliveryInfo));
        eventPublisher.publishEvent(OrderInventoryConfirmEvent.of(order));

        return CompletePayResponseDto.from(order);
    }

    @Transactional
    public OrderClaimResponseDto cancel(AuthenticatedPrincipal me, Long orderId, ClaimRequestDto request) {
        CancelReason reason = CancelReason.find(request.reasonCode())
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORD_INVALID_REASON_CODE));

        validateReasonDetail(request.reasonDetail(), reason.detailRequired());

        Order order = getOwnedOrderForUpdate(me, orderId);
        ensureNoClaim(orderId);

        if (order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(OrderErrorCode.ORD_INVALID_STATUS, "결제 완료 주문만 취소할 수 있습니다.");
        }

        CancelEligibilityResponse eligibility = externalService.getCancelEligibility(orderId);

        if (!eligibility.cancelable()) {
            throw new BusinessException(OrderErrorCode.ORD_CONFLICT_RELEASE_STARTED);
        }

        order.requestCancel();
        OrderClaim claim = orderClaimRepository.save(OrderClaim.create(
                order,
                ClaimType.CANCEL,
                RequesterType.USER,
                request.reasonCode(),
                request.reasonDetail(),
                order.getPaymentAmount()
        ));

        externalService.cancelPayment(order.getPaymentId(), "order-cancel-" + orderId, request.reasonCode());
        eventPublisher.publishEvent(OrderInventoryRestoreEvent.of(order));
        return OrderClaimResponseDto.from(claim);
    }

    @Transactional
    public OrderClaimResponseDto requestReturn(AuthenticatedPrincipal me, Long orderId, ReturnRequestDto request) {
        ReturnReason reason = ReturnReason.find(request.reasonCode())
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORD_INVALID_REASON_CODE));

        validateReasonDetail(request.reasonDetail(), false);

        var attachments = request.attachmentsOrEmpty();
        if (reason.evidenceRequired() && attachments.isEmpty()) {
            throw new BusinessException(OrderErrorCode.ORD_MISSING_RETURN_EVIDENCE);
        }

        String objectKeyPrefix = "returns/%d/".formatted(me.userId());
        if (attachments.stream().anyMatch(attachment -> !attachment.objectKey().startsWith(objectKeyPrefix)
                                                        || attachment.objectKey().contains(".."))) {
            throw new BusinessException(OrderErrorCode.ORD_INVALID_RETURN_EVIDENCE);
        }

        Order order = getOwnedOrderForUpdate(me, orderId);
        ensureNoClaim(orderId);

        if (order.getDeliveryStatus() != DeliveryStatus.DELIVERED || order.getDeliveredAt() == null) {
            throw new BusinessException(OrderErrorCode.ORD_INVALID_DELIVERY_STATUS);
        }

        boolean hasColdItem = hasColdItem(order);
        if (reason == ReturnReason.RTN01 && hasColdItem) {
            throw new BusinessException(OrderErrorCode.ORD_INVALID_FRESH_RETURN);
        }

        LocalDateTime deadline = reason == ReturnReason.RTN01
                ? order.getDeliveredAt().plusDays(7)
                : hasColdItem ? order.getDeliveredAt().plusHours(48) : order.getDeliveredAt().plusMonths(3);

        if (LocalDateTime.now().isAfter(deadline)) {
            throw new BusinessException(OrderErrorCode.ORD_EXPIRED_RETURN_PERIOD);
        }

        order.requestReturn();
        OrderClaim claim = OrderClaim.create(
                order,
                ClaimType.RETURN,
                RequesterType.USER,
                request.reasonCode(),
                request.reasonDetail(),
                order.getPaymentAmount()
        );

        attachments.forEach(attachment -> claim.addAttachment(RefundAttachment.create(
                refundAttachmentBucket,
                attachment.objectKey(),
                attachment.originalFileName(),
                attachment.contentType(),
                attachment.fileSize()
        )));

        orderClaimRepository.save(claim);
        eventPublisher.publishEvent(OrderReturnRequestedEvent.of(order));
        return OrderClaimResponseDto.from(claim);
    }

    @Transactional
    public boolean expire(Long orderId, LocalDateTime now) {
        if (orderRepository.expirePayment(orderId, now) == 0) {
            return false;
        }
        eventPublisher.publishEvent(OrderInventoryReleaseEvent.of(getOrder(orderId)));
        return true;
    }

    @Transactional
    public void completeCancel(Long orderId, String eventStatus) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalStateException("주문을 찾을 수 없습니다. orderId=" + orderId));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.info("이미 취소 처리 완료된 주문입니다. orderId={}", orderId);
            return;
        }

        if (!"RESTORED".equals(eventStatus) && !"ALREADY_RESTORED".equals(eventStatus)) {
            throw new IllegalStateException("재고 복구 실패 이벤트 수신. status=" + eventStatus + ", orderId=" + orderId);
        }

        if (order.getStatus() != OrderStatus.CANCEL_PROCESSING) {
            throw new IllegalStateException("취소 처리 중(CANCEL_PROCESSING) 상태가 아닙니다. currentStatus="
                                            + order.getStatus() + ", orderId=" + orderId);
        }

        order.completeCancel();
    }

    private Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORD_NOT_FOUND_ORDER));
    }

    private Order getOwnedOrder(AuthenticatedPrincipal me, Long orderId) {
        Order order = getOrder(orderId);
        if (!me.isAdmin() && !order.getMemberId().equals(me.userId())) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
        return order;
    }

    private Order getOwnedOrderForUpdate(AuthenticatedPrincipal me, Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORD_NOT_FOUND_ORDER));
        if (!me.isAdmin() && !order.getMemberId().equals(me.userId())) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
        return order;
    }

    private void ensureNoClaim(Long orderId) {
        if (orderClaimRepository.existsByOrderId(orderId)) {
            throw new BusinessException(OrderErrorCode.ORD_CONFLICT_ALREADY_CLAIMED);
        }
    }

    private void validateReasonDetail(String reasonDetail, boolean detailRequired) {
        if (reasonDetail != null && reasonDetail.length() > 500) {
            throw new BusinessException(OrderErrorCode.ORD_INVALID_REASON_DETAIL);
        }
        if (detailRequired && (reasonDetail == null || reasonDetail.isBlank())) {
            throw new BusinessException(OrderErrorCode.ORD_INVALID_REASON_DETAIL);
        }
    }

    private boolean hasColdItem(Order order) {
        return order.getItems().stream()
                .anyMatch(item -> item.getStorageType() == StorageType.REFRIGERATED || item.getStorageType() == StorageType.FROZEN);
    }

    private boolean inventoryItemsMatch(List<CartItem> requested,
                                        List<CheckoutInventoryResponseDto.Item> received) {
        Map<InventoryItemKey, Long> requestedItems = requested.stream()
                .map(item -> new InventoryItemKey(item.getProductId(), item.getQuantity()))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        Map<InventoryItemKey, Long> receivedItems = received.stream()
                .map(item -> new InventoryItemKey(item.productId(), item.quantity()))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return requestedItems.equals(receivedItems);
    }

    private ClaimType parseClaimType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ClaimType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(OrderErrorCode.ORD_INVALID_REQUEST_TYPE);
        }
    }

    private ClaimStatus parseClaimStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ClaimStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(OrderErrorCode.ORD_INVALID_REQUEST_STATUS);
        }
    }

    private record InventoryItemKey(Long productId, Integer quantity) {
    }
}
