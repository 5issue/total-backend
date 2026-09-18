package com.kurly.auth.application.dto;

import com.kurly.auth.infrastructure.security.jwt.IssuedToken;

/**
 * 재발급 결과. refresh token은 쿠키로, access token은 응답 본문으로 나간다(인증인가_설계서 1.4).
 *
 * @param subject 발급한 토큰의 {@code sub} 클레임. 회원은 {@code userId}, 관리자는 {@code adminId}다.
 *                응답에 다시 싣기 위해 토큰을 되파싱하지 않도록 발급 시점 값을 들고 다닌다.
 */
public record TokenPair(IssuedToken accessToken, IssuedToken refreshToken, Long subject) {
}
