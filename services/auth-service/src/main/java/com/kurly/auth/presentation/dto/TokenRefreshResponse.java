package com.kurly.auth.presentation.dto;

/**
 * @param expiresIn access token 유효기간(초)
 */
public record TokenRefreshResponse(String accessToken, long expiresIn) {
}
