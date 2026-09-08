package com.kurly.auth.infrastructure.security.jwt;

import java.time.Duration;
import java.time.Instant;

/**
 * 발급 결과.
 *
 * @param ttl 발급 시점 기준 유효기간. 응답의 {@code expiresIn}과 쿠키 {@code Max-Age}에 그대로 쓴다.
 *            {@code expiresAt}에서 역산하면 경과 시간만큼 1초씩 깎이므로 발급 당시 값을 그대로 보관한다.
 */
public record IssuedToken(String token, Instant expiresAt, Duration ttl, String jti) {
}
