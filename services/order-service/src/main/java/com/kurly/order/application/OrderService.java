package com.kurly.order.application;

import com.kurly.common.exception.BusinessException;
import com.kurly.order.domain.cart.Cart;
import com.kurly.order.domain.cart.CartRepository;
import com.kurly.order.domain.claim.*;
import com.kurly.order.domain.common.OrderErrorCode;
import com.kurly.order.domain.common.StorageType;
import com.kurly.order.domain.order.*;
import com.kurly.order.presentation.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private static final Duration PAYMENT_TIMEOUT = Duration.ofMinutes(5);
    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final OrderClaimRepository orderClaimRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderExternalService externalService;

    @Value("${services.storage.refund-attachment-bucket:refund-attachments}")
    private String refundAttachmentBucket;

    @Transactional
    public CheckoutResponseDto checkout(Long memberId, CheckoutRequestDto request) {
        Cart cart = cartRepository.findByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORD_INVALID_CART_ITEMS));
        if (cart.getAddressId() == null) {
            throw new BusinessException(OrderErrorCode.ORD_NOT_FOUND_ADDRESS);
        }
        var selectedItems = cart.getItems().stream().filter(item -> request.cartItemIds().contains(item.getId())).toList();
        if (selectedItems.isEmpty() || selectedItems.size() != request.cartItemIds().stream().distinct().count()) {
            throw new BusinessException(OrderErrorCode.ORD_INVALID_CART_ITEMS);
        }
        orderRepository.findActiveCheckoutForUpdate(memberId).ifPresent(existing -> {
            if (existing.getInventoryReservationToken() != null && existing.getInventoryReservedUntil().isAfter(LocalDateTime.now())) {
                externalService.releaseInventory(existing.getInventoryReservationToken());
            }
            existing.markExpired();
        });
        CheckoutInventoryResponseDto hold = externalService.holdInventory(selectedItems);
        var orderItems = hold.items().stream().map(item -> OrderItem.create(
                item.productId(), item.dealProductId(), item.skuId(), item.productName(), item.optionName(),
                item.storageType(), item.quantity(), item.unitPrice())).toList();
        Order order = orderRepository.save(Order.createCheckout(
                "O" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(), memberId,
                hold.reservationToken(), hold.expiresAt(), hold.shippingFee(), orderItems));
        return CheckoutResponseDto.from(order);
    }

    public OrderPageResponseDto getAll(Long memberId, String range, String productName, int page, int size) {
        int months = switch (range) {
            case "3M" -> 3;
            case "6M" -> 6;
            case "1Y" -> 12;
            case "3Y" -> 36;
            default -> throw new BusinessException(OrderErrorCode.ORD_INVALID_STATUS, "조회 기간 설정이 올바르지 않습니다.");
        };
        if (page < 1 || size < 1 || size > 100 || (productName != null && productName.length() > 100)) {
            throw new BusinessException(OrderErrorCode.ORD_INVALID_STATUS, "조회 조건이 올바르지 않습니다.");
        }
        var orders = orderRepository.findOrders(memberId, LocalDateTime.now().minusMonths(months),
                productName == null || productName.isBlank() ? null : productName,
                PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return OrderPageResponseDto.from(orders, page, size);
    }

    public OrderDetailResponseDto getById(Long memberId, Long orderId) {
        Order order = getOwnedOrder(memberId, orderId);
        if (order.getStatus() == OrderStatus.CHECKOUT_CREATED || order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(OrderErrorCode.ORD_NOT_FOUND_ORDER);
        }
        return OrderDetailResponseDto.from(order);
    }

    public ClaimHistoryPageResponseDto getClaimHistories(Long memberId, String requestType,
                                                         String requestStatus, int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException(OrderErrorCode.ORD_INVALID_PAGE);
        }
        ClaimType type = parseClaimType(requestType);
        ClaimStatus status = parseClaimStatus(requestStatus);
        var result = orderClaimRepository.findClaims(memberId, type, status,
                PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "requestedAt")));
        return ClaimHistoryPageResponseDto.from(result, page, size);
    }

    public ReturnPreviewResponseDto getReturnPreview(Long memberId, Long orderId) {
        Order order = getOwnedOrder(memberId, orderId);
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
        StorageType policy = hasColdItem ? order.getItems().stream()
                                           .map(item -> item.getStorageType()).filter(type -> type != StorageType.ROOM).findFirst().orElse(StorageType.CHILLED)
                : StorageType.ROOM;
        return new ReturnPreviewResponseDto(orderId, true, policy, reasons,
                new ReturnPreviewResponseDto.RefundPreview(order.getPaymentAmount(), 0L, order.getPaymentAmount()),
                new ReturnPreviewResponseDto.ReturnPolicy(!hasColdItem,
                        hasColdItem ? "상품 사진 확인 후 자체 폐기 여부가 결정됩니다." : "회수 및 검수 후 환불됩니다."));
    }

    @Transactional
    public PlaceOrderResponseDto placeOrder(Long memberId, Long orderId) {
        Order order = getOwnedOrderForUpdate(memberId, orderId);
        if (order.getStatus() == OrderStatus.PAID) {
            throw new BusinessException(OrderErrorCode.ORD_CONFLICT_ALREADY_PAID);
        }
        if (order.getStatus() != OrderStatus.CHECKOUT_CREATED) {
            throw new BusinessException(OrderErrorCode.ORD_INVALID_STATUS);
        }
        order.markPaymentPending(LocalDateTime.now().plus(PAYMENT_TIMEOUT));
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
        Order before = getOrder(orderId);
        if (before.getStatus() == OrderStatus.PAID) {
            throw new BusinessException(OrderErrorCode.ORD_CONFLICT_ALREADY_PAID);
        }
        if (!before.getPaymentAmount().equals(request.paymentAmount())) {
            throw new BusinessException(OrderErrorCode.ORD_INVALID_STATUS, "주문 금액과 결제 금액이 일치하지 않습니다.");
        }
        if (orderRepository.completePayment(orderId, request.paymentId(), request.paidAt(), LocalDateTime.now()) == 0) {
            throw new BusinessException(OrderErrorCode.ORD_EXPIRED_TIMEOUT);
        }
        Order paidOrder = getOrder(orderId);
        eventPublisher.publishEvent(OrderEvent.of("order.inventory.confirm", paidOrder));
        return CompletePayResponseDto.from(paidOrder);
    }

    @Transactional
    public OrderClaimResponseDto cancel(Long memberId, Long orderId, ClaimRequestDto request) {
        CancelReason reason = CancelReason.find(request.reasonCode())
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORD_INVALID_REASON_CODE));
        validateReasonDetail(request, reason.detailRequired());
        Order order = getOwnedOrderForUpdate(memberId, orderId);
        ensureNoClaim(orderId);
        if (order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(OrderErrorCode.ORD_INVALID_STATUS, "결제 완료 주문만 취소할 수 있습니다.");
        }
        if (!externalService.isCancellationEligible(orderId)) {
            throw new BusinessException(OrderErrorCode.ORD_INVALID_STATUS, "이미 출고 처리가 시작되어 취소할 수 없습니다.");
        }
        order.requestCancel();
        OrderClaim claim = orderClaimRepository.save(OrderClaim.create(order, ClaimType.CANCEL,
                RequesterType.USER, request.reasonCode(), request.reasonDetail(), order.getPaymentAmount()));
        eventPublisher.publishEvent(PaymentCancellationEvent.of(order));
        return OrderClaimResponseDto.from(claim);
    }

    @Transactional
    public OrderClaimResponseDto requestReturn(Long memberId, Long orderId, ReturnRequestDto request) {
        ReturnReason reason = ReturnReason.find(request.reasonCode())
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORD_INVALID_REASON_CODE));
        validateReasonDetail(request.reasonDetail(), false);
        var attachments = request.attachmentsOrEmpty();
        if (reason.evidenceRequired() && attachments.isEmpty()) {
            throw new BusinessException(OrderErrorCode.ORD_MISSING_RETURN_EVIDENCE);
        }
        String objectKeyPrefix = "returns/%d/".formatted(memberId);
        if (attachments.stream().anyMatch(attachment -> !attachment.objectKey().startsWith(objectKeyPrefix)
                || attachment.objectKey().contains(".."))) {
            throw new BusinessException(OrderErrorCode.ORD_INVALID_RETURN_EVIDENCE);
        }
        Order order = getOwnedOrderForUpdate(memberId, orderId);
        ensureNoClaim(orderId);
        if (order.getDeliveryStatus() != DeliveryStatus.DELIVERED) {
            throw new BusinessException(OrderErrorCode.ORD_INVALID_DELIVERY_STATUS);
        }
        boolean hasColdItem = hasColdItem(order);
        if (reason == ReturnReason.RTN01 && hasColdItem) {
            throw new BusinessException(OrderErrorCode.ORD_RESTRICTED_FRESH_RETURN);
        }
        if (order.getDeliveredAt() == null) {
            throw new BusinessException(OrderErrorCode.ORD_INVALID_DELIVERY_STATUS);
        }
        LocalDateTime deadline = reason == ReturnReason.RTN01
                ? order.getDeliveredAt().plusDays(7)
                : hasColdItem ? order.getDeliveredAt().plusHours(48) : order.getDeliveredAt().plusMonths(3);
        if (LocalDateTime.now().isAfter(deadline)) {
            throw new BusinessException(OrderErrorCode.ORD_EXPIRED_RETURN_PERIOD);
        }
        order.requestReturn();
        OrderClaim claim = OrderClaim.create(order, ClaimType.RETURN,
                RequesterType.USER, request.reasonCode(), request.reasonDetail(), order.getPaymentAmount());
        attachments.forEach(attachment -> claim.addAttachment(RefundAttachment.create(
                refundAttachmentBucket, attachment.objectKey(), attachment.originalFileName(),
                attachment.contentType(), attachment.fileSize())));
        orderClaimRepository.save(claim);
        eventPublisher.publishEvent(OrderEvent.of("order.return-requested", order));
        return OrderClaimResponseDto.from(claim);
    }

    @Transactional
    public boolean expire(Long orderId, LocalDateTime now) {
        if (orderRepository.expirePayment(orderId, now) == 0) {
            return false;
        }
        eventPublisher.publishEvent(OrderEvent.of("order.inventory.release", getOrder(orderId)));
        return true;
    }

    private Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORD_NOT_FOUND_ORDER));
    }

    private Order getOwnedOrder(Long memberId, Long orderId) {
        Order order = getOrder(orderId);
        if (!order.getMemberId().equals(memberId)) {
            throw new BusinessException(OrderErrorCode.ORD_FORBIDDEN_OWNERSHIP);
        }
        return order;
    }

    private Order getOwnedOrderForUpdate(Long memberId, Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORD_NOT_FOUND_ORDER));
        if (!order.getMemberId().equals(memberId)) {
            throw new BusinessException(OrderErrorCode.ORD_FORBIDDEN_OWNERSHIP);
        }
        return order;
    }

    private void ensureNoClaim(Long orderId) {
        if (orderClaimRepository.existsByOrderId(orderId)) {
            throw new BusinessException(OrderErrorCode.ORD_CONFLICT_ALREADY_CLAIMED);
        }
    }

    private void validateReasonDetail(ClaimRequestDto request, boolean detailRequired) {
        validateReasonDetail(request.reasonDetail(), detailRequired);
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
                .anyMatch(item -> item.getStorageType() == StorageType.CHILLED || item.getStorageType() == StorageType.FROZEN);
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
}
