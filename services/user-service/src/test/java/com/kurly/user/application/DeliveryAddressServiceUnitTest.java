package com.kurly.user.application;

import com.kurly.user.domain.entity.DeliveryAddress;
import com.kurly.user.domain.repository.DeliveryAddressRepository;
import com.kurly.user.presentation.dto.CreateAddressRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DeliveryAddressServiceUnitTest {

    private static final Long USER_ID = 1L;

    @Mock DeliveryAddressRepository deliveryAddressRepository;
    @InjectMocks DeliveryAddressService deliveryAddressService;

    private static DeliveryAddress address(Long id, boolean isDefault) {
        DeliveryAddress a = DeliveryAddress.builder()
                .userId(USER_ID)
                .addressName("우리집")
                .recipientName("홍길동")
                .phone("010-1234-5678")
                .zipCode("06234")
                .address("서울시 강남구 테헤란로 123")
                .addressDetail("101동 202호")
                .defaultAddress(isDefault)
                .accessMethod("공동현관 비밀번호 (1234#)")
                .build();
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    private static CreateAddressRequest request(Boolean isDefault) {
        return new CreateAddressRequest("회사", "홍길동", "010-1234-5678", "06234",
                "서울시 강남구 테헤란로 123", "101동 201호", isDefault, null);
    }

    @Nested
    @DisplayName("조회")
    class FindTest {

        @Test
        void 목록은_저장소_정렬을_그대로_돌려준다() {
            List<DeliveryAddress> stored = List.of(address(2L, true), address(1L, false));
            given(deliveryAddressRepository.findAllByUserIdOrderByDefaultAddressDescIdDesc(USER_ID))
                    .willReturn(stored);

            assertThat(deliveryAddressService.findAll(USER_ID)).isEqualTo(stored);
        }

        @Test
        void 기본_배송지가_없으면_비어있다() {
            given(deliveryAddressRepository.findAllByUserIdAndDefaultAddressTrue(USER_ID))
                    .willReturn(List.of());

            assertThat(deliveryAddressService.findDefault(USER_ID)).isEmpty();
        }

        @Test
        void 기본_배송지가_여러_건이어도_한_건만_돌려준다() {
            given(deliveryAddressRepository.findAllByUserIdAndDefaultAddressTrue(USER_ID))
                    .willReturn(List.of(address(1L, true), address(2L, true)));

            assertThat(deliveryAddressService.findDefault(USER_ID))
                    .map(DeliveryAddress::getId).contains(1L);
        }
    }

    @Nested
    @DisplayName("등록")
    class CreateTest {

        private DeliveryAddress captureSaved() {
            ArgumentCaptor<DeliveryAddress> captor = ArgumentCaptor.forClass(DeliveryAddress.class);
            org.mockito.Mockito.verify(deliveryAddressRepository).save(captor.capture());
            return captor.getValue();
        }

        @Test
        void 첫_배송지는_요청값이_false여도_기본이_된다() {
            given(deliveryAddressRepository.countByUserId(USER_ID)).willReturn(0L);
            given(deliveryAddressRepository.findAllByUserIdAndDefaultAddressTrue(USER_ID)).willReturn(List.of());
            given(deliveryAddressRepository.save(any())).willAnswer(i -> i.getArgument(0));

            deliveryAddressService.create(USER_ID, request(false));

            assertThat(captureSaved().isDefaultAddress()).isTrue();
        }

        @Test
        void 두번째_이후_배송지는_요청값을_따른다() {
            given(deliveryAddressRepository.countByUserId(USER_ID)).willReturn(1L);
            given(deliveryAddressRepository.save(any())).willAnswer(i -> i.getArgument(0));

            deliveryAddressService.create(USER_ID, request(false));

            assertThat(captureSaved().isDefaultAddress()).isFalse();
        }

        @Test
        void isDefault를_생략하면_기본이_아니다() {
            given(deliveryAddressRepository.countByUserId(USER_ID)).willReturn(1L);
            given(deliveryAddressRepository.save(any())).willAnswer(i -> i.getArgument(0));

            deliveryAddressService.create(USER_ID, request(null));

            assertThat(captureSaved().isDefaultAddress()).isFalse();
        }

        @Test
        void 기본으로_등록하면_기존_기본이_해제된다() {
            DeliveryAddress previous = address(1L, true);
            given(deliveryAddressRepository.countByUserId(USER_ID)).willReturn(1L);
            given(deliveryAddressRepository.findAllByUserIdAndDefaultAddressTrue(USER_ID))
                    .willReturn(List.of(previous));
            given(deliveryAddressRepository.save(any())).willAnswer(i -> i.getArgument(0));

            deliveryAddressService.create(USER_ID, request(true));

            assertThat(previous.isDefaultAddress()).isFalse();
            assertThat(captureSaved().isDefaultAddress()).isTrue();
        }

        @Test
        void 요청_필드가_그대로_저장된다() {
            given(deliveryAddressRepository.countByUserId(USER_ID)).willReturn(1L);
            given(deliveryAddressRepository.save(any())).willAnswer(i -> i.getArgument(0));

            deliveryAddressService.create(USER_ID, request(false));

            assertThat(captureSaved())
                    .extracting(DeliveryAddress::getUserId, DeliveryAddress::getAddressName,
                            DeliveryAddress::getRecipientName, DeliveryAddress::getPhone,
                            DeliveryAddress::getZipCode, DeliveryAddress::getAddressDetail)
                    .containsExactly(USER_ID, "회사", "홍길동", "010-1234-5678", "06234", "101동 201호");
        }
    }

    @Nested
    @DisplayName("기본 배송지 변경")
    class SetDefaultTest {

        @Test
        void 대상_배송지가_기본이_된다() {
            DeliveryAddress target = address(2L, false);
            given(deliveryAddressRepository.findByIdAndUserId(2L, USER_ID)).willReturn(Optional.of(target));
            given(deliveryAddressRepository.findAllByUserIdAndDefaultAddressTrue(USER_ID)).willReturn(List.of());

            assertThat(deliveryAddressService.setDefault(USER_ID, 2L).isDefaultAddress()).isTrue();
        }

        @Test
        void 기존_기본_배송지는_해제된다() {
            DeliveryAddress previous = address(1L, true);
            DeliveryAddress target = address(2L, false);
            given(deliveryAddressRepository.findByIdAndUserId(2L, USER_ID)).willReturn(Optional.of(target));
            given(deliveryAddressRepository.findAllByUserIdAndDefaultAddressTrue(USER_ID))
                    .willReturn(List.of(previous));

            deliveryAddressService.setDefault(USER_ID, 2L);

            assertThat(previous.isDefaultAddress()).isFalse();
            assertThat(target.isDefaultAddress()).isTrue();
        }

        @Test
        void 이미_기본인_배송지를_다시_지정해도_기본으로_남는다() {
            DeliveryAddress target = address(2L, true);
            given(deliveryAddressRepository.findByIdAndUserId(2L, USER_ID)).willReturn(Optional.of(target));
            // 해제 대상 목록에 자기 자신이 들어온다. 같은 영속성 컨텍스트의 동일 인스턴스이므로
            // 해제 후 다시 설정되어 최종적으로 기본으로 남아야 한다.
            given(deliveryAddressRepository.findAllByUserIdAndDefaultAddressTrue(USER_ID))
                    .willReturn(List.of(target));

            assertThat(deliveryAddressService.setDefault(USER_ID, 2L).isDefaultAddress()).isTrue();
        }
    }
}
