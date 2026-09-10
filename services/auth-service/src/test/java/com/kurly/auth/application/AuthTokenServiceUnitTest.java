package com.kurly.auth.application;

import com.kurly.auth.domain.entity.AuthAdmin;
import com.kurly.auth.domain.entity.AuthUser;
import com.kurly.auth.domain.entity.UserRefreshToken;
import com.kurly.auth.domain.enums.AuthProvider;
import com.kurly.auth.domain.repository.AdminRefreshTokenRepository;
import com.kurly.auth.domain.repository.UserRefreshTokenRepository;
import com.kurly.auth.infrastructure.security.RefreshTokenHasher;
import com.kurly.auth.infrastructure.security.jwt.IssuedToken;
import com.kurly.auth.infrastructure.security.jwt.JwtTokenProvider;
import com.kurly.common.security.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthTokenServiceUnitTest {

    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock RefreshTokenHasher refreshTokenHasher;
    @Mock UserRefreshTokenRepository userRefreshTokenRepository;
    @Mock AdminRefreshTokenRepository adminRefreshTokenRepository;

    @InjectMocks AuthTokenService authTokenService;

    private static IssuedToken issued(String value) {
        return new IssuedToken(value, Instant.now().plusSeconds(1800), Duration.ofMinutes(30), "jti");
    }

    @Nested
    @DisplayName("로그아웃")
    class LogoutTest {

        @Test
        void 회원은_회원_세션만_제거한다() {
            authTokenService.logout(50001L, Role.USER);

            verify(userRefreshTokenRepository).deleteAllByAuthUserUserId(50001L);
            verify(adminRefreshTokenRepository, never()).deleteAllByAuthAdminAdminId(any());
        }

        @Test
        void 관리자는_관리자_세션만_제거한다() {
            authTokenService.logout(1001L, Role.ADMIN);

            verify(adminRefreshTokenRepository).deleteAllByAuthAdminAdminId(1001L);
            verify(userRefreshTokenRepository, never()).deleteAllByAuthUserUserId(any());
        }
    }

    @Nested
    @DisplayName("토큰 발급")
    class IssueTest {

        @Test
        void 회원_토큰의_sub는_회원도메인_참조값이다() {
            AuthUser user = AuthUser.builder()
                    .provider(AuthProvider.KAKAO).providerId("pid").userId(50001L).build();
            given(jwtTokenProvider.issueAccessToken(50001L, Role.USER)).willReturn(issued("access"));
            given(jwtTokenProvider.issueRefreshToken(50001L, Role.USER)).willReturn(issued("refresh"));
            given(refreshTokenHasher.hash("refresh")).willReturn("hashed");

            authTokenService.issueUserTokens(user);

            verify(jwtTokenProvider).issueAccessToken(50001L, Role.USER);
        }

        @Test
        void 회원_refresh_token은_해시로_저장된다() {
            AuthUser user = AuthUser.builder()
                    .provider(AuthProvider.KAKAO).providerId("pid").userId(50001L).build();
            given(jwtTokenProvider.issueAccessToken(any(), any())).willReturn(issued("access"));
            given(jwtTokenProvider.issueRefreshToken(any(), any())).willReturn(issued("refresh-raw"));
            given(refreshTokenHasher.hash("refresh-raw")).willReturn("hashed-value");

            authTokenService.issueUserTokens(user);

            ArgumentCaptor<UserRefreshToken> saved = ArgumentCaptor.forClass(UserRefreshToken.class);
            verify(userRefreshTokenRepository).save(saved.capture());
            assertThat(saved.getValue().getToken()).isEqualTo("hashed-value");
            assertThat(saved.getValue().getToken()).isNotEqualTo("refresh-raw");
        }

        @Test
        void 관리자_토큰의_sub는_관리자도메인_참조값이다() {
            AuthAdmin admin = AuthAdmin.builder()
                    .loginId("ops").password("h").adminId(1001L).build();
            given(jwtTokenProvider.issueAccessToken(1001L, Role.ADMIN)).willReturn(issued("access"));
            given(jwtTokenProvider.issueRefreshToken(1001L, Role.ADMIN)).willReturn(issued("refresh"));
            given(refreshTokenHasher.hash("refresh")).willReturn("hashed");

            authTokenService.issueAdminTokens(admin);

            verify(jwtTokenProvider).issueAccessToken(1001L, Role.ADMIN);
        }
    }
}
