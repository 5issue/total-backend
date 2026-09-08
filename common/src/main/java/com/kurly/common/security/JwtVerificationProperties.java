package com.kurly.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 공통 인증 처리기 설정.
 *
 * @param enabled       처리기 활성 여부. 기본 비활성이라 준비되지 않은 서비스가 갑자기 차단되지 않는다
 * @param issuer        허용할 발급자. 전 서비스가 동일해야 한다 (설계서 1.3.2)
 * @param audience      허용할 대상
 * @param jwksUri       auth-service의 JWKS 엔드포인트. 자체 {@code JWKSource} 빈을 등록한 서비스는 불필요
 * @param clockSkew     시각 오차 허용치
 * @param fallbackJwk   JWKS 조회 실패 시 사용할 공개키(JWK JSON). auth-service 장애 중
 *                      콜드 스타트한 서비스가 검증을 전면 실패하지 않도록 하는 안전장치
 * @param auditPackages 기동 시 인가 애노테이션 누락을 검사할 패키지
 */
@ConfigurationProperties(prefix = "kurly.security")
public record JwtVerificationProperties(
        boolean enabled,
        String issuer,
        String audience,
        String jwksUri,
        Duration clockSkew,
        String fallbackJwk,
        String[] auditPackages
) {

    private static final Duration DEFAULT_CLOCK_SKEW = Duration.ofSeconds(30);
    private static final String[] DEFAULT_AUDIT_PACKAGES = {"com.kurly"};

    public JwtVerificationProperties {
        clockSkew = clockSkew == null ? DEFAULT_CLOCK_SKEW : clockSkew;
        auditPackages = auditPackages == null || auditPackages.length == 0
                ? DEFAULT_AUDIT_PACKAGES
                : auditPackages;
    }
}
