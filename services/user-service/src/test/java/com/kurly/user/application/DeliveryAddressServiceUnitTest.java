package com.kurly.user.application;

import com.kurly.user.domain.entity.DeliveryAddress;
import com.kurly.user.domain.entity.User;
import com.kurly.user.domain.enums.AuthProvider;
import com.kurly.user.domain.repository.DeliveryAddressRepository;
import com.kurly.user.domain.repository.UserRepository;
import com.kurly.user.presentation.dto.CreateAddressRequest;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeliveryAddressServiceUnitTest {

    private static final Long USER_ID = 1L;

    @Mock DeliveryAddressRepository deliveryAddressRepository;
    @Mock UserRepository userRepository;
    @InjectMocks DeliveryAddressService deliveryAddressService;

    /** 기본 배송지 전환은 회원 행을 잠그고 시작한다. */
    @BeforeEach
    void lockableUser() {
        User user = User.builder().provider(AuthProvider.KAKAO).providerId("p").build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));
    }

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

    private DeliveryAddress captureSaved() {
        ArgumentCaptor<DeliveryAddress> captor = ArgumentCaptor.forClass(DeliveryAddress.class);
        verify(deliveryAddressRepository).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("등록")
    class CreateTest {

        @Test
        void 첫_배송지는_요청값이_false여도_기본이_된다() {
            given(deliveryAddressRepository.countByUserId(USER_ID)).willReturn(0L);
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
            verify(deliveryAddressRepository, never()).clearDefaultOf(any());
        }

        @Test
        void isDefault를_생략하면_기본이_아니다() {
            given(deliveryAddressRepository.countByUserId(USER_ID)).willReturn(1L);
            given(deliveryAddressRepository.save(any())).willAnswer(i -> i.getArgument(0));

            deliveryAddressService.create(USER_ID, request(null));

            assertThat(captureSaved().isDefaultAddress()).isFalse();
        }

        @Test
        void 기본으로_등록하면_기존_기본을_먼저_해제한다() {
            // 해제는 벌크 갱신으로 즉시 실행된다. 더티 체킹에 맡기면 신규 지정과 순서가 엉켜
            // 순간적으로 기본이 둘이 되고 유니크 제약에 걸린다.
            given(deliveryAddressRepository.countByUserId(USER_ID)).willReturn(1L);
            given(deliveryAddressRepository.save(any())).willAnswer(i -> i.getArgument(0));

            deliveryAddressService.create(USER_ID, request(true));

            var inOrder = org.mockito.Mockito.inOrder(deliveryAddressRepository);
            inOrder.verify(deliveryAddressRepository).clearDefaultOf(USER_ID);
            inOrder.verify(deliveryAddressRepository).save(any());
            assertThat(captureSaved().isDefaultAddress()).isTrue();
        }

        @Test
        void 회원_행을_잠그고_시작한다() {
            // 잠그지 않으면 동시 요청이 각자 기존 기본을 해제한 뒤 서로 다른 배송지를 기본으로 만든다.
            given(deliveryAddressRepository.countByUserId(USER_ID)).willReturn(1L);
            given(deliveryAddressRepository.save(any())).willAnswer(i -> i.getArgument(0));

            deliveryAddressService.create(USER_ID, request(false));

            verify(userRepository).findByIdForUpdate(USER_ID);
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
            given(deliveryAddressRepository.findByIdAndUserId(2L, USER_ID))
                    .willReturn(Optional.of(address(2L, false)));

            assertThat(deliveryAddressService.setDefault(USER_ID, 2L).isDefaultAddress()).isTrue();
        }

        @Test
        void 기존_기본을_먼저_해제한다() {
            given(deliveryAddressRepository.findByIdAndUserId(2L, USER_ID))
                    .willReturn(Optional.of(address(2L, false)));

            deliveryAddressService.setDefault(USER_ID, 2L);

            verify(deliveryAddressRepository).clearDefaultOf(USER_ID);
        }

        @Test
        void 이미_기본인_배송지는_건드리지_않는다() {
            // 해제 후 다시 지정하면 더티 체킹이 "변경 없음"으로 보고 갱신을 생략해,
            // 벌크 해제만 반영되어 기본 배송지가 사라진다.
            given(deliveryAddressRepository.findByIdAndUserId(2L, USER_ID))
                    .willReturn(Optional.of(address(2L, true)));

            assertThat(deliveryAddressService.setDefault(USER_ID, 2L).isDefaultAddress()).isTrue();

            verify(deliveryAddressRepository, never()).clearDefaultOf(any());
        }

        @Test
        void 회원_행을_잠그고_시작한다() {
            given(deliveryAddressRepository.findByIdAndUserId(2L, USER_ID))
                    .willReturn(Optional.of(address(2L, false)));

            deliveryAddressService.setDefault(USER_ID, 2L);

            verify(userRepository).findByIdForUpdate(USER_ID);
        }
    }

    @Nested
    @DisplayName("조회")
    class FindTest {

        @BeforeEach
        void 잠금은_조회에_필요없다() {
            org.mockito.Mockito.reset(userRepository);
        }

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
            // DB 유니크 제약이 생기기 전 데이터가 남아 있을 수 있다.
            given(deliveryAddressRepository.findAllByUserIdAndDefaultAddressTrue(USER_ID))
                    .willReturn(List.of(address(1L, true), address(2L, true)));

            assertThat(deliveryAddressService.findDefault(USER_ID))
                    .map(DeliveryAddress::getId).contains(1L);
        }
    }
}
