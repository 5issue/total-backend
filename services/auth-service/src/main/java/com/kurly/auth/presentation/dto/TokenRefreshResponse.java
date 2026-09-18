package com.kurly.auth.presentation.dto;

/**
 * @param expiresIn access token 유효기간(초)
 * @param userId    토큰 주체. 소셜 콜백이 302로 바뀌며 본문이 사라져 여기로 옮겼다.
 *                  관리자 재발급에서는 {@code adminId}가 실린다.
 */
public record TokenRefreshResponse(String accessToken, long expiresIn, Long userId) {
}
