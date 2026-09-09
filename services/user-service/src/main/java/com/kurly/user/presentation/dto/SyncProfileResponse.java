package com.kurly.user.presentation.dto;

import com.kurly.user.domain.entity.User;

/**
 * @param isNewUser 이번 호출로 회원이 생성됐는지. auth-service가 201/200을 가르는 데 쓴다
 */
public record SyncProfileResponse(
        Long userId,
        String provider,
        String providerId,
        String role,
        String status,
        boolean isNewUser
) {

    /** 소셜 로그인으로 만들어지는 회원의 역할은 항상 USER다. */
    private static final String ROLE_USER = "USER";

    public static SyncProfileResponse of(User user, boolean newUser) {
        return new SyncProfileResponse(
                user.getId(),
                user.getProvider().name(),
                user.getProviderId(),
                ROLE_USER,
                user.getStatus().name(),
                newUser);
    }
}
