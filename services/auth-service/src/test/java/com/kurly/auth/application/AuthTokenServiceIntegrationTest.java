package com.kurly.auth.application;

import com.kurly.auth.application.dto.TokenPair;
import com.kurly.auth.domain.entity.AuthUser;
import com.kurly.auth.domain.entity.UserRefreshToken;
import com.kurly.auth.domain.enums.AuthProvider;
import com.kurly.common.security.Role;
import com.kurly.auth.domain.repository.AuthUserRepository;
import com.kurly.auth.domain.repository.UserRefreshTokenRepository;
import com.kurly.auth.infrastructure.persistence.AuthUserJpaRepository;
import com.kurly.auth.infrastructure.persistence.UserRefreshTokenJpaRepository;
import com.kurly.auth.infrastructure.security.RefreshTokenHasher;
import com.kurly.auth.infrastructure.security.jwt.IssuedToken;
import com.kurly.auth.infrastructure.security.jwt.JwtTokenProvider;
import com.kurly.common.security.TokenType;
import com.kurly.common.exception.UnauthorizedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 서비스가 스스로 커밋하는 동작을 확인해야 하므로 테스트에 @Transactional을 걸지 않는다.
 *
 * <p>로컬 MySQL(docker compose up -d auth-mysql)이 필요하므로 기본 빌드에서는 건너뛴다.
 * 실행: {@code AUTH_INTEGRATION_TEST=true ./gradlew :auth-service:test}
 * 팀에서 Testcontainers를 도입하면 이 조건은 제거할 수 있다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "AUTH_INTEGRATION_TEST", matches = "true")
class AuthTokenServiceIntegrationTest {

    @Autowired AuthTokenService authTokenService;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired RefreshTokenHasher refreshTokenHasher;
    // 저장은 도메인 인터페이스로 호출한다. JpaRepository 타입으로 save()를 부르면
    // 도메인 인터페이스의 save(T)와 CrudRepository의 <S>save(S)가 모두 매칭되어 모호성 오류가 난다.
    @Autowired AuthUserRepository authUserRepository;
    @Autowired UserRefreshTokenRepository userRefreshTokenRepository;
    @Autowired AuthUserJpaRepository authUserJpaRepository;
    @Autowired UserRefreshTokenJpaRepository userRefreshTokenJpaRepository;

    @AfterEach
    void cleanUp() {
        userRefreshTokenJpaRepository.deleteAll();
        authUserJpaRepository.deleteAll();
    }

    private AuthUser createUser() {
        return authUserRepository.save(AuthUser.builder()
                .provider(AuthProvider.KAKAO)
                .providerId("kakao-" + System.nanoTime())
                .userId(System.nanoTime() % 1_000_000)
                .build());
    }

    private String issueStoredRefreshToken(AuthUser user) {
        IssuedToken refresh = jwtTokenProvider.issueRefreshToken(user.getId(), Role.USER);
        userRefreshTokenRepository.save(UserRefreshToken.builder()
                .token(refreshTokenHasher.hash(refresh.token()))
                .expiresAt(LocalDateTime.ofInstant(refresh.expiresAt(), ZoneId.systemDefault()))
                .authUser(user)
                .build());
        return refresh.token();
    }

    @Nested
    @DisplayName("토큰 재발급")
    class RefreshTest {

        @Test
        void 새로운_access_token과_refresh_token이_발급된다() {
            AuthUser user = createUser();
            String oldRefreshToken = issueStoredRefreshToken(user);

            TokenPair result = authTokenService.refresh(oldRefreshToken);

            // 토큰 sub는 인증 도메인 id가 아니라 회원 도메인 참조값이다. 다른 서비스의 소유권 비교 기준과 맞춘다.
            assertThat(jwtTokenProvider.parse(result.accessToken().token(), TokenType.ACCESS).userId())
                    .isEqualTo(user.getUserId());
            assertThat(jwtTokenProvider.parse(result.refreshToken().token(), TokenType.REFRESH).userId())
                    .isEqualTo(user.getUserId());
            assertThat(result.refreshToken().token()).isNotEqualTo(oldRefreshToken);
        }

        @Test
        void 사용된_refresh_token은_폐기되고_새_토큰이_저장된다() {
            AuthUser user = createUser();
            String oldRefreshToken = issueStoredRefreshToken(user);

            TokenPair result = authTokenService.refresh(oldRefreshToken);

            List<UserRefreshToken> stored = userRefreshTokenJpaRepository.findAllByAuthUserId(user.getId());
            assertThat(stored).hasSize(2);
            assertThat(findByRaw(stored, oldRefreshToken).isRevoked()).isTrue();
            assertThat(findByRaw(stored, result.refreshToken().token()).isRevoked()).isFalse();
        }

        private UserRefreshToken findByRaw(List<UserRefreshToken> tokens, String rawToken) {
            String hash = refreshTokenHasher.hash(rawToken);
            return tokens.stream()
                    .filter(token -> token.getToken().equals(hash))
                    .findFirst()
                    .orElseThrow();
        }
    }

    @Nested
    @DisplayName("재사용 감지")
    class ReuseDetectionTest {

        @Test
        void 폐기된_토큰을_재사용하면_해당_회원의_모든_세션이_무효화된다() {
            AuthUser user = createUser();
            String oldRefreshToken = issueStoredRefreshToken(user);
            TokenPair rotated = authTokenService.refresh(oldRefreshToken);

            assertThatThrownBy(() -> authTokenService.refresh(oldRefreshToken))
                    .isInstanceOf(UnauthorizedException.class);

            // 무효화가 커밋되어야 한다. 롤백되면 회전된 토큰이 계속 살아 있게 된다.
            List<UserRefreshToken> stored = userRefreshTokenJpaRepository.findAllByAuthUserId(user.getId());
            assertThat(stored).isNotEmpty();
            assertThat(stored).allMatch(UserRefreshToken::isRevoked);

            assertThatThrownBy(() -> authTokenService.refresh(rotated.refreshToken().token()))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }
}
