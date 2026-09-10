package com.kurly.auth.application.dto;

import com.kurly.auth.infrastructure.security.jwt.IssuedToken;

/**
 * 재발급 결과. refresh token은 쿠키로, access token은 응답 본문으로 나간다(인증인가_설계서 1.4).
 */
public record TokenPair(IssuedToken accessToken, IssuedToken refreshToken) {
}
