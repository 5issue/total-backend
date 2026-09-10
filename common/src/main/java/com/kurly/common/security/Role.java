package com.kurly.common.security;

/**
 * 사용자 역할. 인증인가_설계서 2.1 기준 2종.
 *
 * <p>토큰의 {@code role} 클레임에 <b>대문자 이름 그대로</b> 직렬화된다.
 * 모든 서비스가 이 값을 기준으로 역할 인가를 수행하므로 임의로 변경하면 안 된다.
 */
public enum Role {
    USER,
    ADMIN
}
