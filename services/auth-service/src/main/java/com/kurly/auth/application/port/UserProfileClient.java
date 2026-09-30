package com.kurly.auth.application.port;

import com.kurly.auth.domain.enums.AuthProvider;

/**
 * 회원 도메인(user-service)과의 연동 지점.
 *
 * <p>auth-service는 개인정보를 보관하지 않으므로 회원 레코드는 user-service가 소유한다.
 *
 * <p><b>로그인마다 호출한다.</b> 한때는 신규 가입 시에만 호출했으나, 그러면 가입 시점에 이름을
 * 받지 못한 회원(제공자 동의 항목 미설정 등)의 이름이 <b>영구히 비어 있게 된다.</b> 실제로 운영에서
 * 전 회원의 {@code name}이 null이었다. 로그인마다 넘겨 user-service가 비어 있을 때 채우게 한다.
 */
public interface UserProfileClient {

    /**
     * 소셜 식별자로 회원 프로필을 동기화하고 회원 도메인 id를 받는다.
     * 멱등이므로 재시도해도 중복 생성되지 않는다.
     *
     * @param name 제공자가 알려준 이름. {@code null}일 수 있고, 그때는 기존 값을 건드리지 않는다.
     */
    SyncedProfile syncProfile(AuthProvider provider, String providerId, String name);

    record SyncedProfile(Long userId, boolean newUser) {
    }
}
