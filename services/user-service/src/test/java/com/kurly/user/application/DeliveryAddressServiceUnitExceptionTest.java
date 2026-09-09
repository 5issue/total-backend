package com.kurly.user.application;

import com.kurly.user.domain.repository.DeliveryAddressRepository;
import com.kurly.user.exception.AddressNotFoundException;
import com.kurly.user.exception.UserErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeliveryAddressServiceUnitExceptionTest {

    private static final Long USER_ID = 1L;

    @Mock DeliveryAddressRepository deliveryAddressRepository;
    @InjectMocks DeliveryAddressService deliveryAddressService;

    @Nested
    @DisplayName("기본 배송지 변경 실패")
    class SetDefaultTest {

        @Test
        void 존재하지_않는_배송지는_404다() {
            given(deliveryAddressRepository.findByIdAndUserId(99999L, USER_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> deliveryAddressService.setDefault(USER_ID, 99999L))
                    .isInstanceOf(AddressNotFoundException.class)
                    .hasMessage(UserErrorCode.ADDRESS_NOT_FOUND.getMessage());
        }

        @Test
        void 타인의_배송지도_같은_예외다() {
            // 소유자 조건을 조회에 함께 걸어 두므로, 타인 소유는 애초에 결과가 비어 있다.
            // 없는 것과 남의 것이 응답에서 구분되지 않아야 ID 열거로 존재 여부를 알아낼 수 없다.
            given(deliveryAddressRepository.findByIdAndUserId(3L, USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> deliveryAddressService.setDefault(USER_ID, 3L))
                    .isInstanceOf(AddressNotFoundException.class)
                    .hasMessage(UserErrorCode.ADDRESS_NOT_FOUND.getMessage());
        }

        @Test
        void 실패하면_기존_기본_배송지를_건드리지_않는다() {
            given(deliveryAddressRepository.findByIdAndUserId(any(), any())).willReturn(Optional.empty());

            assertThatThrownBy(() -> deliveryAddressService.setDefault(USER_ID, 99999L))
                    .isInstanceOf(AddressNotFoundException.class);

            verify(deliveryAddressRepository, never()).findAllByUserIdAndDefaultAddressTrue(any());
        }
    }

    @Nested
    @DisplayName("에러 코드")
    class ErrorCodeTest {

        @Test
        void 배송지_없음은_404_USER404다() {
            assertThat(UserErrorCode.ADDRESS_NOT_FOUND.getStatus().value()).isEqualTo(404);
            assertThat(UserErrorCode.ADDRESS_NOT_FOUND.getCode()).isEqualTo("USER404");
        }
    }
}
