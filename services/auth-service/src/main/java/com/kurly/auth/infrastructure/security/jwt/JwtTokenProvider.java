package com.kurly.auth.infrastructure.security.jwt;

import com.kurly.common.security.JwtClaimNames;
import com.kurly.common.security.Role;
import com.kurly.common.security.TokenType;
import com.kurly.common.exception.UnauthorizedException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.proc.JOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

/**
 * 자체 발급 JWT의 서명·검증 담당.
 * 서명 알고리즘은 ES256으로 고정하며, 검증 시 알고리즘 화이트리스트와 iss·aud·exp를 강제한다
 * (인증인가_설계서 1.3.1·1.3.2 — alg:none 및 알고리즘 혼동 공격 차단).
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private static final JWSAlgorithm SIGNING_ALGORITHM = JWSAlgorithm.ES256;
    // 클레임 이름은 검증 측(타 서비스)과 공유되어야 하므로 common에서 가져온다.
    private static final String CLAIM_ROLE = JwtClaimNames.ROLE;
    private static final String CLAIM_TOKEN_TYPE = JwtClaimNames.TOKEN_TYPE;

    /** 서버 간 시계 오차 허용치(초). Nimbus 기본값은 60초이며, 만료 검증이 그만큼 느슨해지므로 명시적으로 좁힌다.*/
    private static final int MAX_CLOCK_SKEW_SECONDS = 30;

    private final JwtKeyProvider keyProvider;
    private final JwtProperties properties;
    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor;

    public JwtTokenProvider(JwtKeyProvider keyProvider, JwtProperties properties) {
        this.keyProvider = keyProvider;
        this.properties = properties;
        this.jwtProcessor = createProcessor(keyProvider, properties);
    }

    public IssuedToken issueAccessToken(Long userId, Role role) {
        return issue(userId, role, TokenType.ACCESS, properties.accessTokenTtl());
    }

    public IssuedToken issueRefreshToken(Long userId, Role role) {
        return issue(userId, role, TokenType.REFRESH, properties.refreshTokenTtl());
    }

    /**
     * 서명·만료·발급자·대상·토큰 용도를 모두 검증한 뒤 클레임을 반환한다.
     *
     * @throws UnauthorizedException 검증에 실패한 모든 경우. 실패 사유를 응답에 노출하지 않는다(시큐어코딩가이드 BE-17).
     */
    public TokenClaims parse(String token, TokenType expectedType) {
        JWTClaimsSet claims;
        try {
            claims = jwtProcessor.process(token, null);
        } catch (Exception e) {
            log.debug("JWT 검증 실패", e);
            throw new UnauthorizedException();
        }

        TokenClaims parsed = toTokenClaims(claims);
        if (parsed.tokenType() != expectedType) {
            log.debug("JWT 용도 불일치: expected={}, actual={}", expectedType, parsed.tokenType());
            throw new UnauthorizedException();
        }
        return parsed;
    }

    private IssuedToken issue(Long userId, Role role, TokenType tokenType, Duration ttl) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(ttl);
        String jti = UUID.randomUUID().toString();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_TOKEN_TYPE, tokenType.name())
                .issuer(properties.issuer())
                .audience(properties.audience())
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .jwtID(jti)
                .build();

        JWSHeader header = new JWSHeader.Builder(SIGNING_ALGORITHM)
                .keyID(keyProvider.activeKeyId())
                .type(JOSEObjectType.JWT)
                .build();

        SignedJWT signedJwt = new SignedJWT(header, claims);
        try {
            signedJwt.sign(keyProvider.signer());
        } catch (Exception e) {
            throw new IllegalStateException("JWT 서명에 실패했습니다.", e);
        }
        return new IssuedToken(signedJwt.serialize(), expiresAt, ttl, jti);
    }

    private TokenClaims toTokenClaims(JWTClaimsSet claims) {
        try {
            return new TokenClaims(
                    Long.valueOf(claims.getSubject()),
                    Role.valueOf(claims.getStringClaim(CLAIM_ROLE)),
                    TokenType.valueOf(claims.getStringClaim(CLAIM_TOKEN_TYPE)),
                    claims.getJWTID(),
                    claims.getExpirationTime().toInstant()
            );
        } catch (Exception e) {
            log.debug("JWT 클레임 해석 실패", e);
            throw new UnauthorizedException();
        }
    }

    private static ConfigurableJWTProcessor<SecurityContext> createProcessor(JwtKeyProvider keyProvider,
                                                                            JwtProperties properties) {
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();

        // 키 회전 시 새 JWKS가 즉시 반영되도록 매 검증마다 공급자에서 공개키를 읽는다.
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(
                SIGNING_ALGORITHM,
                (selector, context) -> selector.select(keyProvider.publicJwkSet())
        ));

        JOSEObjectTypeVerifier<SecurityContext> typeVerifier =
                new DefaultJOSEObjectTypeVerifier<>(JOSEObjectType.JWT);
        processor.setJWSTypeVerifier(typeVerifier);

        DefaultJWTClaimsVerifier<SecurityContext> claimsVerifier = new DefaultJWTClaimsVerifier<>(
                properties.audience(),
                new JWTClaimsSet.Builder().issuer(properties.issuer()).build(),
                Set.of("sub", CLAIM_ROLE, CLAIM_TOKEN_TYPE, "exp", "jti")
        );
        claimsVerifier.setMaxClockSkew(MAX_CLOCK_SKEW_SECONDS);
        processor.setJWTClaimsSetVerifier(claimsVerifier);
        return processor;
    }
}
