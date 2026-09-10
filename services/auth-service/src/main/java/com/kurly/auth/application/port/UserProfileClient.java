package com.kurly.auth.application.port;

import com.kurly.auth.domain.enums.AuthProvider;

/**
 * 회원 도메인(user-service)과의 연동 지점.
 *
 * <p>auth-service는 개인정보를 보관하지 않으므로 회원 레코드는 user-service가 소유한다.
 * <b>로그인 경로에서는 호출하지 않는다</b> — 신규 가입 시에만 필요하다.
 */
public interface UserProfileClient {

    /**
     * 소셜 식별자로 회원 프로필을 동기화하고 회원 도메인 id를 받는다.
     * 멱등이므로 재시도해도 중복 생성되지 않는다.
     */
    SyncedProfile syncProfile(AuthProvider provider, String providerId);

    record SyncedProfile(Long userId, boolean newUser) {
    }
}
