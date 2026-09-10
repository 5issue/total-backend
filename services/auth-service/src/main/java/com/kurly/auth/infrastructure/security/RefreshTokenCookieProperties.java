package com.kurly.auth.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * refresh token 쿠키 속성. 인증인가_설계서 1.4 — HttpOnly + Secure + SameSite.
 *
 * @param path 쿠키 전송 경로. 인증 엔드포인트로 좁혀 다른 API 요청에는 실리지 않게 한다.
 */
@ConfigurationProperties(prefix = "auth.refresh-cookie")
public record RefreshTokenCookieProperties(
        String name,
        String path,
        boolean secure,
        String sameSite
) {
}
