package com.kurly.common.security;

/**
 * 서비스 간 공유되는 JWT 클레임 이름.
 * 발급(auth-service)과 검증(각 서비스)이 같은 값을 써야 하므로 한곳에서 관리한다.
 */
public final class JwtClaimNames {

    /** 사용자 역할. 값은 {@link Role}의 이름. */
    public static final String ROLE = "role";

    /**
     * 토큰 용도. 값은 {@link TokenType}의 이름.
     * JOSE 헤더의 표준 파라미터 {@code typ}과 구분하기 위해 {@code token_type}을 쓴다.
     */
    public static final String TOKEN_TYPE = "token_type";

    private JwtClaimNames() {
    }
}
