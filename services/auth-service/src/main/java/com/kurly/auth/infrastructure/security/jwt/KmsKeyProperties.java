package com.kurly.auth.infrastructure.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * KMS 서명키 설정.
 *
 * @param keyId         서명에 쓸 키. 키 ID·ARN·별칭(alias/…) 모두 가능하다
 * @param previousKeyId 회전 중인 구 키. <b>검증용으로만</b> JWKS에 함께 실린다. 설계서 1.3.2의
 *                      "구 키 7일 병행"을 위한 값이며, 병행 기간이 끝나면 비운다
 * @param region        KMS 리전. 비우면 AWS 기본 조회 순서(환경변수·IRSA 등)를 따른다
 */
@ConfigurationProperties(prefix = "jwt.kms")
public record KmsKeyProperties(String keyId, String previousKeyId, String region) {

    /** 해석되지 않은 플레이스홀더의 흔적. Boot의 Binder는 이를 예외로 만들지 않고 리터럴로 남긴다. */
    private static final String UNRESOLVED_PLACEHOLDER_PREFIX = "${";

    public KmsKeyProperties {
        requireConfigured(keyId, "jwt.kms.key-id");
        // 구 키는 선택이다. 값을 준 경우에만 미주입 여부를 본다.
        if (StringUtils.hasText(previousKeyId) && previousKeyId.startsWith(UNRESOLVED_PLACEHOLDER_PREFIX)) {
            throw new IllegalStateException(
                    "jwt.kms.previous-key-id의 환경변수가 주입되지 않았습니다: " + previousKeyId);
        }
    }

    public boolean hasPreviousKey() {
        return StringUtils.hasText(previousKeyId)
                && !previousKeyId.startsWith(UNRESOLVED_PLACEHOLDER_PREFIX);
    }

    /**
     * 값이 비었거나 미해석 플레이스홀더면 기동을 중단한다.
     *
     * <p>서명키를 못 찾으면 토큰을 아예 발급하지 못한다. 런타임에 드러나기 전에 막는다.
     */
    private static void requireConfigured(String value, String key) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(
                    "%s가 필요합니다. KMS 서명을 쓰려면 반드시 설정해야 합니다.".formatted(key));
        }
        if (value.startsWith(UNRESOLVED_PLACEHOLDER_PREFIX)) {
            throw new IllegalStateException(
                    "%s의 환경변수가 주입되지 않았습니다: %s".formatted(key, value));
        }
    }
}
