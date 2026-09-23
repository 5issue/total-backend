package com.kurly.order.application;

import com.kurly.common.exception.BusinessException;
import com.kurly.common.exception.GlobalErrorCode;
import com.kurly.common.security.AuthenticatedPrincipal;
import com.kurly.order.domain.cart.Cart;
import com.kurly.order.domain.cart.CartItem;
import com.kurly.order.domain.cart.CartRepository;
import com.kurly.order.domain.common.OrderErrorCode;
import com.kurly.order.infrastructure.dto.AddressResponse;
import com.kurly.order.infrastructure.dto.CartProductInfo;
import com.kurly.order.infrastructure.dto.DeliveryAddressResponseDto;
import com.kurly.order.presentation.dto.AddCartItemsRequestDto;
import com.kurly.order.presentation.dto.CartResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final CartExternalService externalService;

    @Transactional
    public CartResponseDto addItems(AuthenticatedPrincipal me, AddCartItemsRequestDto request) {
        List<Long> productIds = request.items().stream()
                .map(AddCartItemsRequestDto.CartItemRequest::productId)
                .distinct()
                .toList();

        List<CartProductInfo> products = externalService.getProducts(productIds);

        Map<Long, CartProductInfo> productMap = products.stream()
                .collect(Collectors.toMap(
                        CartProductInfo::productId,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));

        if (productMap.size() != productIds.size()) {
            throw new BusinessException(OrderErrorCode.ORD_INVALID_CART_ITEMS);
        }

        Cart cart = getOrCreateForUpdate(me.userId());

        for (AddCartItemsRequestDto.CartItemRequest itemReq : request.items()) {
            CartProductInfo product = productMap.get(itemReq.productId());

            if (product == null || product.inventory() == null) {
                throw new BusinessException(OrderErrorCode.ORD_INVALID_CART_ITEMS);
            }

            if (product.inventory().isSoldOut()) {
                throw new BusinessException(OrderErrorCode.ORD_ITEM_SOLD_OUT, product.productId().toString());
            }

            if ("SOLDOUT".equals(product.status())) {
                throw new BusinessException(OrderErrorCode.ORD_ITEM_SOLD_OUT, product.productId().toString());
            }

            if (!"SALE".equals(product.status())) {
                throw new BusinessException(OrderErrorCode.ORD_INVALID_CART_ITEMS, product.productId().toString());
            }

            Optional<CartItem> existingItem = cart.findItemByProductId(itemReq.productId());

            int updatedQuantity = existingItem.map(CartItem::getQuantity).orElse(0) + itemReq.quantity();

            if (updatedQuantity > product.inventory().maxQuantityPerOrder()) {
                throw new BusinessException(OrderErrorCode.ORD_EXCEED_MAX_QUANTITY);
            }
            if (updatedQuantity > product.inventory().availableQuantity()) {
                throw new BusinessException(OrderErrorCode.ORD_INSUFFICIENT_STOCK);
            }

            CartItem item = existingItem.orElseGet(() -> {
                CartItem newItem = CartItem.create(itemReq.productId(), product.storageType(), updatedQuantity);
                cart.addItem(newItem);
                return newItem;
            });

            item.changeQuantity(updatedQuantity);

        }

        return getByMemberId(me);
    }

    @Transactional
    public CartResponseDto getByMemberId(AuthenticatedPrincipal me) {
        Long memberId = me.userId();
        Cart cart = getOrCreateForUpdate(memberId);
        AddressResponse address = externalService.getAddress(memberId, cart.getAddressId());

        if (cart.getAddressId() == null && address != null) {
            cart.updateDeliveryAddress(address.addressId(), null, null);
        }

        List<Long> productIds = cart.getItems().stream()
                .map(CartItem::getProductId)
                .toList();

        List<CartProductInfo> productInfos = externalService.getProducts(productIds);

        Map<Long, CartResponseDto.Product> products = productInfos.stream()
                .map(info -> new CartResponseDto.Product(
                        info.productId(),
                        null,
                        info.name(),
                        info.thumbnailUrl(),
                        info.salePrice(),
                        info.inventory() != null ? info.inventory().maxQuantityPerOrder() : 0,
                        "SALE".equals(info.status())
                        && info.inventory() != null
                        && !info.inventory().isSoldOut()
                        && info.inventory().availableQuantity() > 0,
                        null, // deliveryType
                        info.storageType(),
                        null, // sellerId
                        info.seller(),
                        0L    // deliveryFee
                ))
                .collect(Collectors.toMap(CartResponseDto.Product::productId, Function.identity()));

        if (products.size() != cart.getItems().size()) {
            throw new BusinessException(OrderErrorCode.ORD_INCOMPLETE_PRODUCT_RESPONSE);
        }

        return CartResponseDto.from(cart, address, products);
    }

    @Transactional
    public DeliveryAddressResponseDto updateDeliveryAddress(AuthenticatedPrincipal me, Long addressId) {
        if (addressId == null) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE, "배송지 ID가 올바르지 않습니다.");
        }

        Long memberId = me.userId();
        Cart cart = getOrCreateForUpdate(memberId);
        AddressResponse address = externalService.getAddress(memberId, addressId);

        if (address == null) {
            throw new BusinessException(OrderErrorCode.ORD_NOT_FOUND_ADDRESS);
        }

        DeliveryAddressResponseDto.Promise promise = externalService.getDeliveryPromise(address);
        if (promise == null || !promise.deliverable()) {
            throw new BusinessException(OrderErrorCode.ORD_NOT_FOUND_ADDRESS, "배송이 불가능한 지역입니다.");
        }

        cart.updateDeliveryAddress(addressId, promise.regionId(), promise.deliveryType());

        return new DeliveryAddressResponseDto(
                address,
                promise.deliverable(),
                promise.deliveryType(),
                promise.cutoffAt(),
                promise.expectedDeliveryAt()
        );
    }

    private Cart getOrCreateForUpdate(Long memberId) {
        cartRepository.createIfAbsent(memberId);
        return cartRepository.findByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR, "장바구니 조회에 실패했습니다."));
    }
}
