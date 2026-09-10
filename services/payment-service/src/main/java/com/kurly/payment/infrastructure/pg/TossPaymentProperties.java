package com.kurly.payment.infrastructure.pg;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 토스페이먼츠 연동 설정.
 *
 * @param baseUrl   API 기본 주소
 * @param secretKey 시크릿 키({@code test_sk_...} / {@code live_sk_...}).
 *                  <b>절대 커밋하지 않는다.</b> 로컬은 {@code application-secret.yml},
 *                  운영은 환경변수 {@code TOSS_SECRET_KEY}로 주입한다
 */
@ConfigurationProperties(prefix = "toss")
public record TossPaymentProperties(String baseUrl, String secretKey) {

    private static final String DEFAULT_BASE_URL = "https://api.tosspayments.com";

    /** 해석되지 않은 플레이스홀더의 흔적. Boot의 Binder는 이를 예외로 만들지 않고 리터럴로 남긴다. */
    private static final String UNRESOLVED_PLACEHOLDER_PREFIX = "${";

    public TossPaymentProperties {
        baseUrl = (baseUrl == null || baseUrl.isBlank()) ? DEFAULT_BASE_URL : baseUrl;

        // 키가 없으면 승인·취소가 전부 실패한다. 기동 시점에 잡아내는 편이 낫다.
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "toss.secret-key가 필요합니다. 로컬은 application-secret.yml, 운영은 TOSS_SECRET_KEY 환경변수로 주입하세요.");
        }
        if (secretKey.startsWith(UNRESOLVED_PLACEHOLDER_PREFIX)) {
            throw new IllegalStateException("toss.secret-key의 환경변수가 주입되지 않았습니다: " + secretKey);
        }
    }
}
