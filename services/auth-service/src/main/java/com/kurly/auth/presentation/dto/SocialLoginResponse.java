package com.kurly.auth.presentation.dto;

/**
 * @param expiresIn access token 유효기간(초)
 */
public record SocialLoginResponse(String accessToken, long expiresIn, UserSummary user) {

    /** 개인정보는 담지 않는다. 프로필은 클라이언트가 user-service에서 직접 조회한다. */
    public record UserSummary(Long userId) {
    }
}
