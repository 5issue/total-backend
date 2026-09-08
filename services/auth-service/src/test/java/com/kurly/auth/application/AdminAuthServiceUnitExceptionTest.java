package com.kurly.auth.application;

import com.kurly.auth.domain.entity.AuthAdmin;
import com.kurly.auth.domain.enums.AdminStatus;
import com.kurly.auth.exception.AccountLockedException;
import com.kurly.auth.domain.repository.AuthAdminRepository;
import com.kurly.common.exception.ForbiddenException;
import com.kurly.common.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceUnitExceptionTest {

    private static final String DUMMY_HASH = "$2a$10$dummyhashfortimingdefense";
    private static final String EXPECTED_MESSAGE = "아이디 또는 비밀번호가 일치하지 않습니다.";

    @Mock AuthAdminRepository authAdminRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthTokenService authTokenService;
    @Mock AdminLoginAttemptService adminLoginAttemptService;

    AdminAuthService adminAuthService;

    @BeforeEach
    void setUp() {
        given(passwordEncoder.encode(anyString())).willReturn(DUMMY_HASH);
        adminAuthService = new AdminAuthService(authAdminRepository, passwordEncoder, authTokenService, adminLoginAttemptService);
    }

    @Nested
    @DisplayName("자격 증명 실패")
    class CredentialTest {

        @Test
        void 존재하지_않는_아이디는_거부된다() {
            given(authAdminRepository.findByLoginId("unknown")).willReturn(Optional.empty());

            assertThatThrownBy(() -> adminAuthService.login("unknown", "raw-password"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage(EXPECTED_MESSAGE);

            verify(authTokenService, never()).issueAdminTokens(any());
        }

        @Test
        void 존재하지_않는_아이디여도_해시_검증을_수행해_응답시간_차이를_남기지_않는다() {
            given(authAdminRepository.findByLoginId("unknown")).willReturn(Optional.empty());

            assertThatThrownBy(() -> adminAuthService.login("unknown", "raw-password"))
                    .isInstanceOf(UnauthorizedException.class);

            verify(passwordEncoder).matches("raw-password", DUMMY_HASH);
        }

        @Test
        void 비밀번호가_틀리면_거부된다() {
            AuthAdmin admin = AuthAdmin.builder().loginId("admin1").password("stored-hash").adminId(1001L).build();
            given(authAdminRepository.findByLoginId("admin1")).willReturn(Optional.of(admin));
            given(passwordEncoder.matches("wrong-password", "stored-hash")).willReturn(false);

            assertThatThrownBy(() -> adminAuthService.login("admin1", "wrong-password"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage(EXPECTED_MESSAGE);

            verify(authTokenService, never()).issueAdminTokens(any());
        }

        @Test
        void 아이디가_없을_때와_비밀번호가_틀릴_때의_메시지가_같다() {
            AuthAdmin admin = AuthAdmin.builder().loginId("admin1").password("stored-hash").adminId(1001L).build();
            given(authAdminRepository.findByLoginId("unknown")).willReturn(Optional.empty());
            given(authAdminRepository.findByLoginId("admin1")).willReturn(Optional.of(admin));
            given(passwordEncoder.matches("wrong-password", "stored-hash")).willReturn(false);
            // 아이디가 없는 경로는 더미 해시로 검증하므로 이 호출도 스텁이 필요하다.
            given(passwordEncoder.matches("wrong-password", DUMMY_HASH)).willReturn(false);

            assertThatThrownBy(() -> adminAuthService.login("unknown", "wrong-password"))
                    .hasMessage(EXPECTED_MESSAGE);
            assertThatThrownBy(() -> adminAuthService.login("admin1", "wrong-password"))
                    .hasMessage(EXPECTED_MESSAGE);
        }
    }

    @Nested
    @DisplayName("계정 상태")
    class StatusTest {

        @Test
        void 잠금_시간이_남은_계정은_비밀번호_검증_없이_거부된다() {
            AuthAdmin admin = AuthAdmin.builder().loginId("admin1").password("stored-hash").adminId(1001L).build();
            ReflectionTestUtils.setField(admin, "lockedUntil", LocalDateTime.now().plusMinutes(10));
            given(authAdminRepository.findByLoginId("admin1")).willReturn(Optional.of(admin));

            assertThatThrownBy(() -> adminAuthService.login("admin1", "raw-password"))
                    .isInstanceOf(AccountLockedException.class)
                    .hasMessage("연속적인 비밀번호 오류로 요청이 거부 되었습니다. 10분 후 다시 시도해주세요.");

            // 잠긴 계정에 해시 연산 비용을 쓰지 않는다(반복 시도로 CPU를 소모시키지 못하게 한다).
            verify(passwordEncoder, never()).matches("raw-password", "stored-hash");
            verify(authTokenService, never()).issueAdminTokens(any());
        }

        @Test
        void 잠금_시간이_지난_계정은_정상적으로_검증을_진행한다() {
            AuthAdmin admin = AuthAdmin.builder().loginId("admin1").password("stored-hash").adminId(1001L).build();
            ReflectionTestUtils.setField(admin, "lockedUntil", LocalDateTime.now().minusMinutes(1));
            given(authAdminRepository.findByLoginId("admin1")).willReturn(Optional.of(admin));
            given(passwordEncoder.matches("wrong", "stored-hash")).willReturn(false);

            assertThatThrownBy(() -> adminAuthService.login("admin1", "wrong"))
                    .isInstanceOf(UnauthorizedException.class);

            // 만료된 잠금은 별도 해제 없이 풀리므로 해시 검증까지 진행된다.
            verify(passwordEncoder).matches("wrong", "stored-hash");
        }

        @Test
        void 비활성화된_계정은_403과_전용_메시지를_반환한다() {
            AuthAdmin admin = AuthAdmin.builder().loginId("admin1").password("stored-hash").adminId(1001L).build();
            ReflectionTestUtils.setField(admin, "status", AdminStatus.DISABLED);
            given(authAdminRepository.findByLoginId("admin1")).willReturn(Optional.of(admin));
            given(passwordEncoder.matches("raw-password", "stored-hash")).willReturn(true);

            assertThatThrownBy(() -> adminAuthService.login("admin1", "raw-password"))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("비활성화된 관리자 계정입니다.");

            verify(authTokenService, never()).issueAdminTokens(any());
        }

        @Test
        void 비활성화_여부는_비밀번호가_맞은_뒤에만_드러난다() {
            AuthAdmin admin = AuthAdmin.builder().loginId("admin1").password("stored-hash").adminId(1001L).build();
            ReflectionTestUtils.setField(admin, "status", AdminStatus.DISABLED);
            given(authAdminRepository.findByLoginId("admin1")).willReturn(Optional.of(admin));
            given(passwordEncoder.matches("wrong", "stored-hash")).willReturn(false);

            // 비밀번호가 틀리면 비활성 계정이어도 일반 401로 응답해 계정 상태를 노출하지 않는다.
            assertThatThrownBy(() -> adminAuthService.login("admin1", "wrong"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage(EXPECTED_MESSAGE);
        }
    }

    @Nested
    @DisplayName("실패 횟수 기록")
    class RetryCountTest {

        @Test
        void 비밀번호_불일치_시_실패_횟수를_기록한다() {
            AuthAdmin admin = AuthAdmin.builder().loginId("admin1").password("stored-hash").adminId(1001L).build();
            ReflectionTestUtils.setField(admin, "id", 7L);
            given(authAdminRepository.findByLoginId("admin1")).willReturn(Optional.of(admin));
            given(passwordEncoder.matches("wrong", "stored-hash")).willReturn(false);

            assertThatThrownBy(() -> adminAuthService.login("admin1", "wrong"))
                    .isInstanceOf(UnauthorizedException.class);

            verify(adminLoginAttemptService).recordFailure(7L);
        }

        @Test
        void 존재하지_않는_아이디는_기록하지_않는다() {
            given(authAdminRepository.findByLoginId("unknown")).willReturn(Optional.empty());

            assertThatThrownBy(() -> adminAuthService.login("unknown", "raw-password"))
                    .isInstanceOf(UnauthorizedException.class);

            // 계정이 없으므로 카운터를 둘 대상이 없다. IP 단위 제한은 별도 대응이 필요하다(I-6).
            verify(adminLoginAttemptService, never()).recordFailure(any());
        }
    }
}
