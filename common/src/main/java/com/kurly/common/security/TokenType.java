package com.kurly.common.security;

/**
 * access / refresh 토큰 용도 구분. 토큰의 {@code token_type} 클레임에 담긴다.
 *
 * <p>검증 시 이 값을 확인하지 않으면 유효기간이 긴 refresh token을 access token 자리에 제시해
 * 인증을 통과시킬 수 있다. 토큰을 검증하는 모든 서비스가 반드시 확인해야 한다.
 */
public enum TokenType {
    ACCESS,
    REFRESH
}
