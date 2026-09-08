package com.kurly.auth.infrastructure.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * refresh token은 프론트가 직접 다루지 않고 HttpOnly 쿠키로만 오간다(인증인가_설계서 1.4·1.9).
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookieFactory {

    private final RefreshTokenCookieProperties properties;

    public Optional<String> extract(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> properties.name().equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(StringUtils::hasText)
                .findFirst();
    }

    public ResponseCookie create(String token, Duration ttl) {
        return build(token, ttl);
    }

    /** 로그아웃 시 쿠키를 즉시 만료시킨다. */
    public ResponseCookie expire() {
        return build("", Duration.ZERO);
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(properties.name(), value)
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite(properties.sameSite())
                .path(properties.path())
                .maxAge(maxAge)
                .build();
    }
}
