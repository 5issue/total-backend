package com.kurly.auth.application;

import com.kurly.auth.application.dto.TokenPair;
import com.kurly.auth.domain.entity.AuthAdmin;
import com.kurly.auth.domain.repository.AuthAdminRepository;
import com.kurly.auth.infrastructure.security.jwt.IssuedToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceUnitTest {

    private static final String DUMMY_HASH = "$2a$10$dummyhashfortimingdefense";

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

    private static TokenPair anyTokenPair() {
        IssuedToken access = new IssuedToken("access", Instant.now().plusSeconds(1800), Duration.ofMinutes(30), "a");
        IssuedToken refresh = new IssuedToken("refresh", Instant.now().plusSeconds(1209600), Duration.ofDays(14), "r");
        return new TokenPair(access, refresh);
    }

    @Nested
    @DisplayName("관리자 로그인 정상 처리")
    class LoginTest {

        @Test
        void 아이디와_비밀번호가_일치하면_토큰이_발급된다() {
            AuthAdmin admin = AuthAdmin.builder().loginId("admin1").password("stored-hash").adminId(1001L).build();
            TokenPair expected = anyTokenPair();
            given(authAdminRepository.findByLoginId("admin1")).willReturn(Optional.of(admin));
            given(passwordEncoder.matches("raw-password", "stored-hash")).willReturn(true);
            given(authTokenService.issueAdminTokens(admin)).willReturn(expected);

            TokenPair result = adminAuthService.login("admin1", "raw-password");

            assertThat(result).isSameAs(expected);
        }

        @Test
        void 비밀번호는_평문_비교가_아니라_해시_검증으로_확인한다() {
            AuthAdmin admin = AuthAdmin.builder().loginId("admin1").password("stored-hash").adminId(1001L).build();
            given(authAdminRepository.findByLoginId("admin1")).willReturn(Optional.of(admin));
            given(passwordEncoder.matches("raw-password", "stored-hash")).willReturn(true);
            given(authTokenService.issueAdminTokens(admin)).willReturn(anyTokenPair());

            adminAuthService.login("admin1", "raw-password");

            // stored-hash를 인자로 matches가 호출되었다는 것 자체가 해시 검증 경로를 탔다는 근거다.
            org.mockito.Mockito.verify(passwordEncoder).matches("raw-password", "stored-hash");
        }
    }
}
