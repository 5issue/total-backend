package com.kurly.auth.presentation.controller;

import com.kurly.auth.infrastructure.security.jwt.JwtKeyProvider;
import com.kurly.common.security.PublicApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/**
 * 공개키 배포. 인증인가_설계서 1.3.2 — 각 검증 주체는 이 공개키로 토큰을 검증한다.
 *
 * <p>응답을 {@code ApiResponse}로 감싸지 않는다. JWKS는 RFC 7517이 정한 표준 형식이라
 * 표준 라이브러리(Nimbus 등)가 그대로 파싱하며, 감싸면 해석하지 못한다.
 */
@RestController
@RequiredArgsConstructor
public class JwksController {

    /** 키 회전 주기(6개월)에 비해 충분히 짧게 잡아 회전이 빠르게 전파되도록 한다. */
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final JwtKeyProvider jwtKeyProvider;

    @PublicApi
    @GetMapping("/.well-known/jwks.json")
    public ResponseEntity<Map<String, Object>> jwks() {
        // toJSONObject()는 공개 파라미터만 직렬화한다. 개인키는 포함되지 않는다.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(CACHE_TTL).cachePublic())
                .body(jwtKeyProvider.publicJwkSet().toJSONObject());
    }
}
