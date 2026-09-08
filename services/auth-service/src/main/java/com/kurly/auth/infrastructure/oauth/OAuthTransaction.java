package com.kurly.auth.infrastructure.oauth;

/**
 * 인가 요청과 콜백 사이에 이어져야 하는 값들.
 * Redis 등 서버 저장소를 쓰지 않고 HttpOnly 쿠키로 브라우저에 맡긴다.
 */
public record OAuthTransaction(String state, String codeVerifier, String redirectUri) {
}
