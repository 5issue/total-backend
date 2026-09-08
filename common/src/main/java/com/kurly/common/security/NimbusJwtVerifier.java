package com.kurly.common.security;

import com.kurly.common.exception.UnauthorizedException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * Nimbus 기반 access token 검증기.
 *
 * <p>검증 규칙은 auth-service의 발급 규칙과 짝을 이룬다(인증인가_설계서 1.3.1·1.3.2).
 * <ul>
 *   <li>ES256만 허용 — {@code alg:none}·알고리즘 혼동 공격 차단</li>
 *   <li>{@code kid}로 공개키 선택 — 키 회전 대응</li>
 *   <li>{@code iss}·{@code aud}·{@code exp} 필수 검증</li>
 *   <li>{@code token_type=ACCESS} 강제 — refresh token으로 API를 호출할 수 없게 한다</li>
 * </ul>
 */
@Slf4j
public class NimbusJwtVerifier implements JwtVerifier {

    private static final JWSAlgorithm SIGNING_ALGORITHM = JWSAlgorithm.ES256;

    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor;

    public NimbusJwtVerifier(JWKSource<SecurityContext> jwkSource, JwtVerificationProperties properties) {
        this.jwtProcessor = createProcessor(jwkSource, properties);
    }

    @Override
    public AuthenticatedPrincipal verify(String accessToken) {
        JWTClaimsSet claims;
        try {
            claims = jwtProcessor.process(accessToken, null);
        } catch (Exception e) {
            log.debug("access token 검증 실패", e);
            throw new UnauthorizedException();
        }

        try {
            TokenType tokenType = TokenType.valueOf(claims.getStringClaim(JwtClaimNames.TOKEN_TYPE));
            if (tokenType != TokenType.ACCESS) {
                log.debug("access token이 아닌 토큰으로 요청: tokenType={}", tokenType);
                throw new UnauthorizedException();
            }
            return new AuthenticatedPrincipal(
                    Long.valueOf(claims.getSubject()),
                    Role.valueOf(claims.getStringClaim(JwtClaimNames.ROLE)));
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            log.debug("클레임 해석 실패", e);
            throw new UnauthorizedException();
        }
    }

    private static ConfigurableJWTProcessor<SecurityContext> createProcessor(
            JWKSource<SecurityContext> jwkSource, JwtVerificationProperties properties) {

        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(SIGNING_ALGORITHM, jwkSource));
        processor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(JOSEObjectType.JWT));

        DefaultJWTClaimsVerifier<SecurityContext> claimsVerifier = new DefaultJWTClaimsVerifier<>(
                properties.audience(),
                new JWTClaimsSet.Builder().issuer(properties.issuer()).build(),
                Set.of("sub", JwtClaimNames.ROLE, JwtClaimNames.TOKEN_TYPE, "exp", "jti"));
        // Nimbus 기본값 60초를 그대로 두면 만료 검증이 그만큼 느슨해진다.
        claimsVerifier.setMaxClockSkew((int) properties.clockSkew().toSeconds());
        processor.setJWTClaimsSetVerifier(claimsVerifier);
        return processor;
    }
}
