package com.kurly.common.security;

/**
 * 검증된 토큰에서 추출한 요청 주체.
 *
 * <p>요청 attribute에만 담기고 ThreadLocal에는 두지 않는다. 스레드 풀 재사용 시
 * 정리 누락으로 다음 요청이 이전 사용자의 신원을 물려받는 사고를 원천 차단하기 위함이다.
 * 서비스 계층에서 필요하면 파라미터로 명시적으로 전달한다.
 */
public record AuthenticatedPrincipal(Long userId, Role role) {

    public static final String ATTRIBUTE = AuthenticatedPrincipal.class.getName();

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
