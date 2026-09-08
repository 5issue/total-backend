package com.kurly.order.application;

import com.kurly.order.domain.cart.Cart;
import com.kurly.order.domain.cart.CartRepository;
import com.kurly.order.domain.cart.DeliveryType;
import com.kurly.order.presentation.dto.CartResponseDto;
import com.kurly.order.presentation.dto.DeliveryAddressResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceUnitTest {

    @Mock CartRepository cartRepository;
    @Mock CartExternalService externalService;
    @InjectMocks CartService cartService;

    @Nested
    @DisplayName("장바구니 조회")
    class GetCartTest {

        @Test
        void 장바구니가_없으면_생성하고_기본_배송지를_적용한다() {
            CartResponseDto.Address address = new CartResponseDto.Address(
                    10L, "집", "홍길동", "01000000000", "12345", "서울시", "101호");
            when(cartRepository.findByMemberId(1L)).thenReturn(Optional.empty());
            when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(externalService.getAddress(1L, null)).thenReturn(address);
            when(externalService.getProducts(List.of())).thenReturn(List.of());

            var response = cartService.getByMemberId(1L);

            assertThat(response.selectedAddress()).isEqualTo(address);
            verify(cartRepository).save(any(Cart.class));
        }
    }

    @Nested
    @DisplayName("배송지 변경")
    class UpdateAddressTest {

        @Test
        void 회원_배송지를_검증하고_배송약속을_갱신한다() {
            Cart cart = Cart.create(1L);
            CartResponseDto.Address address = new CartResponseDto.Address(
                    10L, "집", "홍길동", "01000000000", "12345", "서울시", "101호");
            LocalDateTime expectedAt = LocalDateTime.now().plusDays(1);
            DeliveryAddressResponseDto.Promise promise = new DeliveryAddressResponseDto.Promise(
                    true, 20L, DeliveryType.DAWN, LocalDateTime.now().plusHours(2), expectedAt);
            when(cartRepository.findByMemberId(1L)).thenReturn(Optional.of(cart));
            when(externalService.getAddress(1L, 10L)).thenReturn(address);
            when(externalService.getDeliveryPromise(address)).thenReturn(promise);

            var response = cartService.updateDeliveryAddress(1L, 10L);

            assertThat(response.expectedDeliveryAt()).isEqualTo(expectedAt);
            assertThat(cart.getAddressId()).isEqualTo(10L);
            assertThat(cart.getRegionId()).isEqualTo(20L);
            assertThat(cart.getDeliveryType()).isEqualTo(DeliveryType.DAWN);
        }
    }
}
