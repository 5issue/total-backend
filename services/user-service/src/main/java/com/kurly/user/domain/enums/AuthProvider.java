package com.kurly.user.domain.enums;

/**
 * 소셜 제공자. 인증인가_설계서 1.1 기준 카카오·네이버 전용.
 *
 * <p>인증 도메인(auth-service)에도 같은 enum이 있다. 회원 도메인이 이 값을 함께 보관하는 이유는
 * {@code sync-profile}이 (provider, providerId)로 멱등해야 하기 때문이다.
 */
public enum AuthProvider {
    KAKAO,
    NAVER
}
