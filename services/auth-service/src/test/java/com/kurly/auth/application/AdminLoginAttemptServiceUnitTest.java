package com.kurly.auth.application;

import com.kurly.auth.domain.entity.AuthAdmin;
import com.kurly.auth.domain.repository.AuthAdminRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminLoginAttemptServiceUnitTest {

    @Mock AuthAdminRepository authAdminRepository;
    @InjectMocks AdminLoginAttemptService adminLoginAttemptService;

    private AuthAdmin adminWithRetryCount(int retryCount) {
        AuthAdmin admin = AuthAdmin.builder().loginId("ops").password("h").adminId(1L).build();
        ReflectionTestUtils.setField(admin, "retryCount", retryCount);
        return admin;
    }

    @Nested
    @DisplayName("실패 기록")
    class FailureTest {

        @Test
        void 임계치_미만이면_카운터만_올리고_잠그지_않는다() {
            given(authAdminRepository.findById(7L))
                    .willReturn(Optional.of(adminWithRetryCount(AdminLoginAttemptService.MAX_RETRY_COUNT - 1)));

            adminLoginAttemptService.recordFailure(7L);

            verify(authAdminRepository).increaseRetryCount(7L);
            verify(authAdminRepository, never()).lockUntil(any(), any());
        }

        @Test
        void 임계치에_도달하면_잠금_해제시각을_설정한다() {
            given(authAdminRepository.findById(7L))
                    .willReturn(Optional.of(adminWithRetryCount(AdminLoginAttemptService.MAX_RETRY_COUNT)));

            adminLoginAttemptService.recordFailure(7L);

            verify(authAdminRepository).lockUntil(eq(7L), any(LocalDateTime.class));
        }

        @Test
        void 계정이_사라졌으면_잠금을_시도하지_않는다() {
            given(authAdminRepository.findById(7L)).willReturn(Optional.empty());

            adminLoginAttemptService.recordFailure(7L);

            verify(authAdminRepository, never()).lockUntil(any(), any());
        }
    }

    @Nested
    @DisplayName("성공 기록")
    class SuccessTest {

        @Test
        void 실패_횟수와_잠금을_해제한다() {
            adminLoginAttemptService.recordSuccess(7L);

            verify(authAdminRepository).clearLoginFailures(7L);
        }
    }
}
