package com.kurly.order.application;

import com.kurly.common.exception.BusinessException;
import com.kurly.order.domain.cart.Cart;
import com.kurly.order.domain.cart.CartRepository;
import com.kurly.order.domain.common.OrderErrorCode;
import com.kurly.order.presentation.dto.CartResponseDto;
import com.kurly.order.presentation.dto.DeliveryAddressResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        Cart cart = cartRepository.findByMemberId(memberId).orElseGet(() -> cartRepository.save(Cart.create(memberId)));
        CartResponseDto.Address address = externalService.getAddress(memberId, cart.getAddressId());
        if (cart.getAddressId() == null) {
            cart.updateDeliveryAddress(address.addressId(), null, null);
        }
        Map<Long, CartResponseDto.Product> products = externalService.getProducts(
                        cart.getItems().stream().map(item -> item.getProductId()).toList()).stream()
                .collect(Collectors.toMap(CartResponseDto.Product::productId, Function.identity()));
        return CartResponseDto.from(cart, address, products);
    }

    @Transactional
    public DeliveryAddressResponseDto updateDeliveryAddress(Long memberId, Long addressId) {
        Cart cart = cartRepository.findByMemberId(memberId).orElseGet(() -> cartRepository.save(Cart.create(memberId)));
        CartResponseDto.Address address = externalService.getAddress(memberId, addressId);
        DeliveryAddressResponseDto.Promise promise = externalService.getDeliveryPromise(address);
        if (!promise.deliverable()) {
            throw new BusinessException(OrderErrorCode.ORD_NOT_FOUND_ADDRESS, "배송이 불가능한 지역입니다.");
        }
        cart.updateDeliveryAddress(addressId, promise.regionId(), promise.deliveryType());
        return new DeliveryAddressResponseDto(address, promise.deliverable(), promise.deliveryType(),
                promise.cutoffAt(), promise.expectedDeliveryAt());
    }
}
