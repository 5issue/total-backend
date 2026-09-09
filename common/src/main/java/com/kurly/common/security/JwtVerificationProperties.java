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

    /** 해석되지 않은 플레이스홀더의 흔적. Boot의 Binder는 이를 예외로 만들지 않고 리터럴로 남긴다. */
    private static final String UNRESOLVED_PLACEHOLDER_PREFIX = "${";

    public JwtVerificationProperties {
        clockSkew = clockSkew == null ? DEFAULT_CLOCK_SKEW : clockSkew;
        auditPackages = auditPackages == null || auditPackages.length == 0
                ? DEFAULT_AUDIT_PACKAGES
                : auditPackages;

        if (enabled) {
            // audience가 비면 대상 검증이 사실상 꺼져 다른 audience용 토큰이 통과한다.
            // issuer가 비면 발급자 제한이 사라진다. 둘 다 조용히 완화되므로 기동을 막는다.
            requireConfigured(issuer, "kurly.security.issuer");
            requireConfigured(audience, "kurly.security.audience");
        }
    }

    /**
     * 값이 비었거나 미해석 플레이스홀더면 기동을 중단한다.
     *
     * <p>플레이스홀더 검사가 필요한 이유: {@code ${JWT_ISSUER}}처럼 기본값 없이 적어두면
     * 환경변수 누락 시 기동이 실패할 것 같지만, 실제로는 <b>리터럴 문자열이 그대로 바인딩된다.</b>
     * 값이 있는 것처럼 보여 검증을 통과하고, 문제는 한참 뒤 런타임에 드러난다.
     */
    private static void requireConfigured(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "%s가 필요합니다. kurly.security.enabled=true인 서비스는 반드시 설정해야 합니다.".formatted(key));
        }
        if (value.startsWith(UNRESOLVED_PLACEHOLDER_PREFIX)) {
            throw new IllegalStateException(
                    "%s의 환경변수가 주입되지 않았습니다: %s".formatted(key, value));
        }
    }
}
