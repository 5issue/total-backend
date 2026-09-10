package com.kurly.auth.infrastructure.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param privateJwk 로컬·테스트 전용 서명키(EC P-256 JWK JSON). 비워두면 기동 시 임시 키를 생성한다.
 *                   운영에서는 KMS가 개인키를 보관하므로 이 값을 사용하지 않는다(인증인가_설계서 1.3.2).
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String issuer,
        String audience,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        String privateJwk
) {
}
