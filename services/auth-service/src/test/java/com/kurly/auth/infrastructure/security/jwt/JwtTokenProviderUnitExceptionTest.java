package com.kurly.auth.infrastructure.security.jwt;

import com.kurly.common.security.Role;
import com.kurly.common.security.TokenType;
import com.kurly.common.exception.UnauthorizedException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.mock.env.MockEnvironment;

class JwtTokenProviderUnitExceptionTest {

    private static final String ISSUER = "https://auth.kurly.local";
    private static final String AUDIENCE = "kurly-api";

    private static String sharedJwk;

    private final JwtProperties properties = properties(ISSUER, AUDIENCE, Duration.ofMinutes(30));
    private final JwtTokenProvider tokenProvider = provider(properties);

    @BeforeAll
    static void generateSharedKey() throws Exception {
        sharedJwk = new ECKeyGenerator(Curve.P_256)
                .keyID(UUID.randomUUID().toString())
                .algorithm(JWSAlgorithm.ES256)
                .generate()
                .toJSONString();
    }

    private static JwtProperties properties(String issuer, String audience, Duration accessTtl) {
        return new JwtProperties(issuer, audience, accessTtl, Duration.ofDays(14), sharedJwk);
    }

    private static JwtTokenProvider provider(JwtProperties properties) {
        return new JwtTokenProvider(new LocalEcJwtKeyProvider(properties, new MockEnvironment().withProperty("spring.profiles.active", "local")), properties);
    }

    @Nested
    @DisplayName("토큰 용도 검증")
    class TokenTypeTest {

        @Test
        void refresh_token을_access_token으로_사용하면_거부된다() {
            IssuedToken refresh = tokenProvider.issueRefreshToken(1L, Role.USER);

            assertThatThrownBy(() -> tokenProvider.parse(refresh.token(), TokenType.ACCESS))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        void access_token을_refresh_token으로_사용하면_거부된다() {
            IssuedToken access = tokenProvider.issueAccessToken(1L, Role.USER);

            assertThatThrownBy(() -> tokenProvider.parse(access.token(), TokenType.REFRESH))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }

    @Nested
    @DisplayName("서명 검증")
    class SignatureTest {

        @Test
        void 서명이_없는_alg_none_토큰은_거부된다() {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject("1")
                    .claim("role", Role.USER.name())
                    .claim("token_type", TokenType.ACCESS.name())
                    .issuer(ISSUER)
                    .audience(AUDIENCE)
                    .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                    .jwtID(UUID.randomUUID().toString())
                    .build();
            String unsigned = new PlainJWT(claims).serialize();

            assertThatThrownBy(() -> tokenProvider.parse(unsigned, TokenType.ACCESS))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        void 다른_키로_서명된_토큰은_거부된다() throws Exception {
            String otherJwk = new ECKeyGenerator(Curve.P_256)
                    .keyID(UUID.randomUUID().toString())
                    .algorithm(JWSAlgorithm.ES256)
                    .generate()
                    .toJSONString();
            JwtProperties otherProperties = new JwtProperties(
                    ISSUER, AUDIENCE, Duration.ofMinutes(30), Duration.ofDays(14), otherJwk);
            IssuedToken forged = provider(otherProperties).issueAccessToken(1L, Role.USER);

            assertThatThrownBy(() -> tokenProvider.parse(forged.token(), TokenType.ACCESS))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        void 페이로드가_변조된_토큰은_거부된다() {
            IssuedToken issued = tokenProvider.issueAccessToken(1L, Role.USER);
            String[] parts = issued.token().split("\\.");
            String tampered = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "AA." + parts[2];

            assertThatThrownBy(() -> tokenProvider.parse(tampered, TokenType.ACCESS))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        void 형식이_아닌_문자열은_거부된다() {
            assertThatThrownBy(() -> tokenProvider.parse("not-a-jwt", TokenType.ACCESS))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }

    @Nested
    @DisplayName("클레임 검증")
    class ClaimsTest {

        @Test
        void 시계_오차_허용치를_넘겨_만료된_토큰은_거부된다() {
            JwtTokenProvider expiringProvider = provider(properties(ISSUER, AUDIENCE, Duration.ofMinutes(-5)));
            IssuedToken expired = expiringProvider.issueAccessToken(1L, Role.USER);

            assertThatThrownBy(() -> tokenProvider.parse(expired.token(), TokenType.ACCESS))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        void 발급자가_다른_토큰은_거부된다() {
            JwtTokenProvider otherIssuer =
                    provider(properties("https://evil.example.com", AUDIENCE, Duration.ofMinutes(30)));
            IssuedToken foreign = otherIssuer.issueAccessToken(1L, Role.USER);

            assertThatThrownBy(() -> tokenProvider.parse(foreign.token(), TokenType.ACCESS))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        void 대상이_다른_토큰은_거부된다() {
            JwtTokenProvider otherAudience =
                    provider(properties(ISSUER, "other-api", Duration.ofMinutes(30)));
            IssuedToken foreign = otherAudience.issueAccessToken(1L, Role.USER);

            assertThatThrownBy(() -> tokenProvider.parse(foreign.token(), TokenType.ACCESS))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }
}
