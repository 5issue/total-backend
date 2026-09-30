package com.kurly.user.application;

import com.kurly.user.domain.entity.DeliveryAddress;
import com.kurly.user.domain.entity.User;
import com.kurly.user.domain.enums.AuthProvider;
import com.kurly.user.domain.enums.UserStatus;
import com.kurly.user.domain.repository.UserRepository;
import com.kurly.user.presentation.dto.UserProfileResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceUnitTest {

    private static final String PROVIDER_ID = "999888777";

    @Mock UserRepository userRepository;
    @Mock DeliveryAddressService deliveryAddressService;
    @InjectMocks UserProfileService userProfileService;

    private static User user(Long id, String name) {
        User u = User.builder()
                .provider(AuthProvider.KAKAO)
                .providerId(PROVIDER_ID)
                .name(name)
                .build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private static DeliveryAddress defaultAddress() {
        DeliveryAddress a = DeliveryAddress.builder()
                .userId(1L)
                .addressName("우리집")
                .recipientName("홍길동")
                .phone("010-1234-5678")
                .zipCode("06234")
                .address("서울시 강남구 테헤란로 123")
                .addressDetail("101동 202호")
                .defaultAddress(true)
                .accessMethod("공동현관 비밀번호 (1234#)")
                .build();
        ReflectionTestUtils.setField(a, "id", 105L);
        return a;
    }

    @Nested
    @DisplayName("프로필 동기화")
    class SyncTest {

        @Test
        void 기존_회원이면_새로_만들지_않는다() {
            User existing = user(1L, "홍길동");
            given(userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, PROVIDER_ID))
                    .willReturn(Optional.of(existing));

            UserProfileService.SyncResult result =
                    userProfileService.syncProfile(AuthProvider.KAKAO, PROVIDER_ID, null);

            assertThat(result.user()).isEqualTo(existing);
            assertThat(result.newUser()).isFalse();
            verify(userRepository, org.mockito.Mockito.never()).save(any());
        }

        @Test
        void 없는_회원이면_ACTIVE로_생성한다() {
            given(userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, PROVIDER_ID))
                    .willReturn(Optional.empty());
            given(userRepository.save(any())).willAnswer(i -> i.getArgument(0));

            UserProfileService.SyncResult result =
                    userProfileService.syncProfile(AuthProvider.KAKAO, PROVIDER_ID, null);

            assertThat(result.newUser()).isTrue();

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue())
                    .extracting(User::getProvider, User::getProviderId, User::getStatus)
                    .containsExactly(AuthProvider.KAKAO, PROVIDER_ID, UserStatus.ACTIVE);
        }

        @Test
        void 동시_생성으로_유니크_제약에_걸리면_먼저_만들어진_회원을_쓴다() {
            User winner = user(1L, "홍길동");
            given(userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, PROVIDER_ID))
                    .willReturn(Optional.empty())
                    .willReturn(Optional.of(winner));
            given(userRepository.save(any())).willThrow(new DataIntegrityViolationException("duplicate"));

            UserProfileService.SyncResult result =
                    userProfileService.syncProfile(AuthProvider.KAKAO, PROVIDER_ID, null);

            assertThat(result.user()).isEqualTo(winner);
            assertThat(result.newUser()).isFalse();
        }

        @Test
        void 신규_회원이면_제공자가_준_이름을_저장한다() {
            given(userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, PROVIDER_ID))
                    .willReturn(Optional.empty());
            given(userRepository.save(any())).willAnswer(i -> i.getArgument(0));

            userProfileService.syncProfile(AuthProvider.KAKAO, PROVIDER_ID, "김컬리");

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("김컬리");
        }

        @Test
        void 이름이_비어_있는_기존_회원은_제공자_이름으로_채운다() {
            User existing = user(1L, null);
            given(userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, PROVIDER_ID))
                    .willReturn(Optional.of(existing));
            given(userRepository.fillNameIfBlank(1L, "김컬리")).willReturn(1);

            UserProfileService.SyncResult result =
                    userProfileService.syncProfile(AuthProvider.KAKAO, PROVIDER_ID, "김컬리");

            assertThat(result.user().getName()).isEqualTo("김컬리");
            // merge가 되는 save로는 다른 컬럼까지 되돌아간다. name만 건드리는 UPDATE여야 한다.
            verify(userRepository).fillNameIfBlank(1L, "김컬리");
            verify(userRepository, org.mockito.Mockito.never()).save(any());
        }

        @Test
        void 동시_보정에서_졌으면_먼저_저장된_이름을_덮어쓰지_않는다() {
            User existing = user(1L, null);
            given(userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, PROVIDER_ID))
                    .willReturn(Optional.of(existing));
            // 0행 = 조회 이후 다른 요청이 먼저 채웠다. DB가 승자를 정한다.
            given(userRepository.fillNameIfBlank(1L, "늦게온이름")).willReturn(0);

            UserProfileService.SyncResult result =
                    userProfileService.syncProfile(AuthProvider.KAKAO, PROVIDER_ID, "늦게온이름");

            assertThat(result.user().getName()).isNull();
            verify(userRepository, org.mockito.Mockito.never()).save(any());
        }

        @Test
        void 이름_앞뒤_공백은_제거해서_저장한다() {
            User existing = user(1L, null);
            given(userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, PROVIDER_ID))
                    .willReturn(Optional.of(existing));
            given(userRepository.fillNameIfBlank(1L, "김컬리")).willReturn(1);

            userProfileService.syncProfile(AuthProvider.KAKAO, PROVIDER_ID, "  김컬리  ");

            verify(userRepository).fillNameIfBlank(1L, "김컬리");
        }

        @Test
        void 이미_이름이_있는_회원은_제공자_이름으로_덮어쓰지_않는다() {
            User existing = user(1L, "직접바꾼이름");
            given(userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, PROVIDER_ID))
                    .willReturn(Optional.of(existing));

            UserProfileService.SyncResult result =
                    userProfileService.syncProfile(AuthProvider.KAKAO, PROVIDER_ID, "김컬리");

            assertThat(result.user().getName()).isEqualTo("직접바꾼이름");
            verify(userRepository, org.mockito.Mockito.never()).save(any());
            verify(userRepository, org.mockito.Mockito.never()).fillNameIfBlank(any(), any());
        }

        @Test
        void 제공자가_이름을_주지_않으면_기존_회원을_건드리지_않는다() {
            User existing = user(1L, null);
            given(userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, PROVIDER_ID))
                    .willReturn(Optional.of(existing));

            userProfileService.syncProfile(AuthProvider.KAKAO, PROVIDER_ID, null);

            verify(userRepository, org.mockito.Mockito.never()).save(any());
            verify(userRepository, org.mockito.Mockito.never()).fillNameIfBlank(any(), any());
        }

        @Test
        void 공백만_있는_이름은_저장하지_않는다() {
            User existing = user(1L, null);
            given(userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, PROVIDER_ID))
                    .willReturn(Optional.of(existing));

            userProfileService.syncProfile(AuthProvider.KAKAO, PROVIDER_ID, "   ");

            assertThat(existing.getName()).isNull();
            verify(userRepository, org.mockito.Mockito.never()).save(any());
            verify(userRepository, org.mockito.Mockito.never()).fillNameIfBlank(any(), any());
        }
    }

    @Nested
    @DisplayName("주문 프로필 조회")
    class ProfileTest {

        @Test
        void 이름과_기본_배송지를_함께_돌려준다() {
            given(userRepository.findById(1L)).willReturn(Optional.of(user(1L, "홍길동")));
            given(deliveryAddressService.findDefault(1L)).willReturn(Optional.of(defaultAddress()));

            UserProfileResponse response = userProfileService.getProfile(1L);

            assertThat(response.name()).isEqualTo("홍길동");
            assertThat(response.defaultAddress().addressId()).isEqualTo(105L);
            assertThat(response.defaultAddress().addressDetail()).isEqualTo("101동 202호");
        }

        @Test
        void 배송지가_없으면_기본_배송지는_null이다() {
            given(userRepository.findById(1L)).willReturn(Optional.of(user(1L, "홍길동")));
            given(deliveryAddressService.findDefault(1L)).willReturn(Optional.empty());

            assertThat(userProfileService.getProfile(1L).defaultAddress()).isNull();
        }

        @Test
        void 소셜에서_이름을_받지_못했으면_이름은_null이다() {
            given(userRepository.findById(1L)).willReturn(Optional.of(user(1L, null)));
            given(deliveryAddressService.findDefault(1L)).willReturn(Optional.empty());

            assertThat(userProfileService.getProfile(1L).name()).isNull();
        }
    }
}
