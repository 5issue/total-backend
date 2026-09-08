package com.kurly.auth.infrastructure.security.jwt;

import com.kurly.common.security.Role;
import com.kurly.common.security.TokenType;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderUnitTest {

    private static final String ISSUER = "https://auth.kurly.local";
    private static final String AUDIENCE = "kurly-api";

    private final JwtProperties properties = new JwtProperties(
            ISSUER, AUDIENCE, Duration.ofMinutes(30), Duration.ofDays(14), null);
    private final JwtTokenProvider tokenProvider =
            new JwtTokenProvider(new LocalEcJwtKeyProvider(properties), properties);

    @Nested
    @DisplayName("토큰 발급")
    class IssueTest {

        @Test
        void access_token을_발급하고_클레임을_다시_읽을_수_있다() {
            IssuedToken issued = tokenProvider.issueAccessToken(1L, Role.USER);

            TokenClaims claims = tokenProvider.parse(issued.token(), TokenType.ACCESS);

            assertThat(claims.userId()).isEqualTo(1L);
            assertThat(claims.role()).isEqualTo(Role.USER);
            assertThat(claims.tokenType()).isEqualTo(TokenType.ACCESS);
            assertThat(claims.jti()).isEqualTo(issued.jti());
        }

        @Test
        void refresh_token을_발급하고_클레임을_다시_읽을_수_있다() {
            IssuedToken issued = tokenProvider.issueRefreshToken(7L, Role.ADMIN);

            TokenClaims claims = tokenProvider.parse(issued.token(), TokenType.REFRESH);

            assertThat(claims.userId()).isEqualTo(7L);
            assertThat(claims.role()).isEqualTo(Role.ADMIN);
            assertThat(claims.tokenType()).isEqualTo(TokenType.REFRESH);
        }

        @Test
        void 설계서에_정의된_만료시간이_적용된다() {
            Instant before = Instant.now();

            IssuedToken access = tokenProvider.issueAccessToken(1L, Role.USER);
            IssuedToken refresh = tokenProvider.issueRefreshToken(1L, Role.USER);

            assertThat(access.expiresAt()).isBetween(
                    before.plus(Duration.ofMinutes(30)).minusSeconds(5),
                    Instant.now().plus(Duration.ofMinutes(30)).plusSeconds(5));
            assertThat(refresh.expiresAt()).isBetween(
                    before.plus(Duration.ofDays(14)).minusSeconds(5),
                    Instant.now().plus(Duration.ofDays(14)).plusSeconds(5));
        }

        @Test
        void 매_발급마다_서로_다른_jti가_부여된다() {
            IssuedToken first = tokenProvider.issueAccessToken(1L, Role.USER);
            IssuedToken second = tokenProvider.issueAccessToken(1L, Role.USER);

            assertThat(first.jti()).isNotEqualTo(second.jti());
        }
    }

    @Nested
    @DisplayName("헤더 및 클레임 규격")
    class TokenFormatTest {

        @Test
        void 키_회전을_위해_헤더에_kid가_포함된다() throws Exception {
            IssuedToken issued = tokenProvider.issueAccessToken(1L, Role.USER);

            SignedJWT jwt = SignedJWT.parse(issued.token());

            assertThat(jwt.getHeader().getKeyID()).isNotBlank();
        }

        @Test
        void 서명_알고리즘은_ES256이다() throws Exception {
            IssuedToken issued = tokenProvider.issueAccessToken(1L, Role.USER);

            SignedJWT jwt = SignedJWT.parse(issued.token());

            assertThat(jwt.getHeader().getAlgorithm().getName()).isEqualTo("ES256");
        }

        @Test
        void 발급자와_대상이_클레임에_담긴다() throws Exception {
            IssuedToken issued = tokenProvider.issueAccessToken(1L, Role.USER);

            SignedJWT jwt = SignedJWT.parse(issued.token());

            assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo(ISSUER);
            assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly(AUDIENCE);
        }
    }
}
