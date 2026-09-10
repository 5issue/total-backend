package com.kurly.user.application;

import com.kurly.common.exception.UnauthorizedException;
import com.kurly.user.domain.enums.AuthProvider;
import com.kurly.user.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceUnitExceptionTest {

    private static final String PROVIDER_ID = "999888777";

    @Mock UserRepository userRepository;
    @Mock DeliveryAddressService deliveryAddressService;
    @InjectMocks UserProfileService userProfileService;

    @Nested
    @DisplayName("주문 프로필 조회 실패")
    class ProfileTest {

        @Test
        void 토큰의_주체가_사라졌으면_401이다() {
            // 탈퇴 등으로 회원이 없어진 토큰이다. 실패 사유를 더 드러내지 않고 인증 실패로 처리한다.
            given(userRepository.findById(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userProfileService.getProfile(1L))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("인증이 필요합니다.");
        }
    }

    @Nested
    @DisplayName("프로필 동기화 실패")
    class SyncTest {

        @Test
        void 제약_위반_뒤_재조회에도_없으면_원래_예외를_그대로_던진다() {
            // 유니크 제약이 아닌 다른 무결성 위반(예: 컬럼 길이 초과)이면 재조회해도 회원이 없다.
            // 이때 성공으로 위장하지 않고 원래 예외를 올려보내야 한다.
            DataIntegrityViolationException cause = new DataIntegrityViolationException("not a duplicate");
            given(userRepository.findByProviderAndProviderId(AuthProvider.NAVER, PROVIDER_ID))
                    .willReturn(Optional.empty());
            given(userRepository.save(any())).willThrow(cause);

            assertThatThrownBy(() -> userProfileService.syncProfile(AuthProvider.NAVER, PROVIDER_ID))
                    .isSameAs(cause);
        }
    }
}
