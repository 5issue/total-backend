package com.kurly.auth.application;

import com.kurly.auth.domain.entity.AdminRefreshToken;
import com.kurly.auth.domain.entity.AuthAdmin;
import com.kurly.auth.domain.entity.AuthUser;
import com.kurly.auth.domain.entity.UserRefreshToken;
import com.kurly.auth.domain.enums.AdminStatus;
import com.kurly.auth.domain.enums.AuthProvider;
import com.kurly.auth.domain.enums.UserStatus;
import com.kurly.auth.domain.repository.AdminRefreshTokenRepository;
import com.kurly.auth.domain.repository.UserRefreshTokenRepository;
import com.kurly.auth.infrastructure.security.RefreshTokenHasher;
import com.kurly.auth.infrastructure.security.jwt.JwtTokenProvider;
import com.kurly.auth.infrastructure.security.jwt.TokenClaims;
import com.kurly.common.exception.UnauthorizedException;
import com.kurly.common.security.Role;
import com.kurly.common.security.TokenType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthTokenServiceUnitExceptionTest {

    private static final String RAW = "raw-refresh-token";
    private static final String HASH = "hashed-refresh-token";

    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock RefreshTokenHasher refreshTokenHasher;
    @Mock UserRefreshTokenRepository userRefreshTokenRepository;
    @Mock AdminRefreshTokenRepository adminRefreshTokenRepository;

    @InjectMocks AuthTokenService authTokenService;

    @BeforeEach
    void stubParsing() {
        given(refreshTokenHasher.hash(RAW)).willReturn(HASH);
    }

    private void givenRole(Role role) {
        given(jwtTokenProvider.parse(RAW, TokenType.REFRESH))
                .willReturn(new TokenClaims(1L, role, TokenType.REFRESH, "jti", Instant.now()));
    }

    private AuthUser user() {
        return AuthUser.builder().provider(AuthProvider.KAKAO).providerId("pid").userId(50001L).build();
    }

    private AuthAdmin admin() {
        return AuthAdmin.builder().loginId("ops").password("h").adminId(1001L).build();
    }

    private UserRefreshToken userToken(AuthUser owner, LocalDateTime expiresAt) {
        return UserRefreshToken.builder().token(HASH).expiresAt(expiresAt).authUser(owner).build();
    }

    private AdminRefreshToken adminToken(AuthAdmin owner, LocalDateTime expiresAt) {
        return AdminRefreshToken.builder().token(HASH).expiresAt(expiresAt).authAdmin(owner).build();
    }

    @Nested
    @DisplayName("회원 재발급 실패")
    class UserRefreshTest {

        @Test
        void 저장되지_않은_토큰은_거부된다() {
            givenRole(Role.USER);
            given(userRefreshTokenRepository.findByToken(HASH)).willReturn(Optional.empty());

            assertThatThrownBy(() -> authTokenService.refresh(RAW))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        void 폐기된_토큰_재사용이면_세션_전체를_무효화한다() {
            givenRole(Role.USER);
            AuthUser owner = user();
            UserRefreshToken revoked = userToken(owner, LocalDateTime.now().plusDays(1));
            revoked.revoke();
            UserRefreshToken live = userToken(owner, LocalDateTime.now().plusDays(1));
            given(userRefreshTokenRepository.findByToken(HASH)).willReturn(Optional.of(revoked));
            given(userRefreshTokenRepository.findAllByAuthUserId(any())).willReturn(List.of(revoked, live));

            assertThatThrownBy(() -> authTokenService.refresh(RAW))
                    .isInstanceOf(UnauthorizedException.class);

            // 살아 있던 세션까지 함께 무효화되어야 한다.
            assertThat(live.isRevoked()).isTrue();
        }

        @Test
        void 저장소_기준으로_만료된_토큰은_거부된다() {
            givenRole(Role.USER);
            given(userRefreshTokenRepository.findByToken(HASH))
                    .willReturn(Optional.of(userToken(user(), LocalDateTime.now().minusMinutes(1))));

            assertThatThrownBy(() -> authTokenService.refresh(RAW))
                    .isInstanceOf(UnauthorizedException.class);

            verify(jwtTokenProvider, never()).issueAccessToken(any(), any());
        }

        @Test
        void 탈퇴한_회원은_갱신할_수_없다() {
            givenRole(Role.USER);
            AuthUser withdrawn = user();
            ReflectionTestUtils.setField(withdrawn, "status", UserStatus.WITHDRAWN);
            given(userRefreshTokenRepository.findByToken(HASH))
                    .willReturn(Optional.of(userToken(withdrawn, LocalDateTime.now().plusDays(1))));

            assertThatThrownBy(() -> authTokenService.refresh(RAW))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }

    @Nested
    @DisplayName("관리자 재발급 실패")
    class AdminRefreshTest {

        @Test
        void 저장되지_않은_토큰은_거부된다() {
            givenRole(Role.ADMIN);
            given(adminRefreshTokenRepository.findByToken(HASH)).willReturn(Optional.empty());

            assertThatThrownBy(() -> authTokenService.refresh(RAW))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        void 폐기된_토큰_재사용이면_세션_전체를_무효화한다() {
            givenRole(Role.ADMIN);
            AuthAdmin owner = admin();
            AdminRefreshToken revoked = adminToken(owner, LocalDateTime.now().plusDays(1));
            revoked.revoke();
            AdminRefreshToken live = adminToken(owner, LocalDateTime.now().plusDays(1));
            given(adminRefreshTokenRepository.findByToken(HASH)).willReturn(Optional.of(revoked));
            given(adminRefreshTokenRepository.findAllByAuthAdminId(any())).willReturn(List.of(revoked, live));

            assertThatThrownBy(() -> authTokenService.refresh(RAW))
                    .isInstanceOf(UnauthorizedException.class);

            assertThat(live.isRevoked()).isTrue();
        }

        @Test
        void 만료된_토큰은_거부된다() {
            givenRole(Role.ADMIN);
            given(adminRefreshTokenRepository.findByToken(HASH))
                    .willReturn(Optional.of(adminToken(admin(), LocalDateTime.now().minusMinutes(1))));

            assertThatThrownBy(() -> authTokenService.refresh(RAW))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        void 비활성화된_관리자는_갱신할_수_없다() {
            givenRole(Role.ADMIN);
            AuthAdmin disabled = admin();
            ReflectionTestUtils.setField(disabled, "status", AdminStatus.DISABLED);
            given(adminRefreshTokenRepository.findByToken(HASH))
                    .willReturn(Optional.of(adminToken(disabled, LocalDateTime.now().plusDays(1))));

            assertThatThrownBy(() -> authTokenService.refresh(RAW))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }
}
