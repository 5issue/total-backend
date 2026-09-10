package com.kurly.order.application;

import com.kurly.common.exception.BusinessException;
import com.kurly.common.exception.GlobalErrorCode;
import com.kurly.order.domain.cart.Cart;
import com.kurly.order.domain.cart.CartRepository;
import com.kurly.order.domain.common.OrderErrorCode;
import com.kurly.order.presentation.dto.CartResponseDto;
import com.kurly.order.presentation.dto.DeliveryAddressResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

    private final CartRepository cartRepository;
    private final CartExternalService externalService;

    @Transactional
    public CartResponseDto getByMemberId(Long memberId) {
        Cart cart = getOrCreateForUpdate(memberId);
        CartResponseDto.Address address = externalService.getAddress(memberId, cart.getAddressId());

        if (cart.getAddressId() == null && address != null) {
            cart.updateDeliveryAddress(address.addressId(), null, null);
        }

        List<Long> productIds = cart.getItems().stream()
                .map(item -> item.getProductId())
                .toList();

        Map<Long, CartResponseDto.Product> products = externalService.getProducts(productIds).stream()
                .collect(Collectors.toMap(CartResponseDto.Product::productId, Function.identity()));

        if (products.size() != cart.getItems().size()) {
            throw new BusinessException(OrderErrorCode.ORD_INCOMPLETE_PRODUCT_RESPONSE);
        }

        return CartResponseDto.from(cart, address, products);
    }

    @Transactional
    public DeliveryAddressResponseDto updateDeliveryAddress(Long memberId, Long addressId) {
        if (addressId == null) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE, "배송지 ID가 올바르지 않습니다.");
        }

        Cart cart = getOrCreateForUpdate(memberId);
        CartResponseDto.Address address = externalService.getAddress(memberId, addressId);

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