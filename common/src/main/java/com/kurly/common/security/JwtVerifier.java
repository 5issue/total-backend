package com.kurly.common.security;

import com.kurly.common.exception.UnauthorizedException;

/**
 * access token 검증기. 각 서비스가 자신에게 들어온 요청의 토큰을 스스로 검증한다
 * (인증인가_설계서 3.4).
 */
public interface JwtVerifier {

    /**
     * 서명·발급자·대상·만료·토큰 용도를 검증하고 주체를 반환한다.
     *
     * @throws UnauthorizedException 검증에 실패한 모든 경우. 사유는 응답에 노출하지 않는다(BE-17)
     */
    AuthenticatedPrincipal verify(String accessToken);
}
