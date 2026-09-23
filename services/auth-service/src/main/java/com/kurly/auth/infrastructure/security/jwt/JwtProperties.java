package com.kurly.auth.infrastructure.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param adminAccessTokenTtl 관리자 access token 수명. <b>유휴 한도 이하로 둔다.</b>
 *                            유휴 판정은 갱신 시점에만 일어나므로, access token이 유휴 한도보다
 *                            오래 살면 한도를 넘긴 뒤에도 만료 전까지 보호 API를 계속 호출할 수 있다.
 *                            실효 차단 상한은 {@code 유휴 한도 + 이 값}이다.
 * @param privateJwk 로컬·테스트 전용 서명키(EC P-256 JWK JSON). 비워두면 기동 시 임시 키를 생성한다.
 *                   운영에서는 KMS가 개인키를 보관하므로 이 값을 사용하지 않는다(인증인가_설계서 1.3.2).
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String issuer,
        String audience,
        Duration accessTokenTtl,
        Duration adminAccessTokenTtl,
        Duration refreshTokenTtl,
        String privateJwk
) {
}
