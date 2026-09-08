package com.kurly.auth.application.dto;

/**
 * @param newUser 최초 로그인(회원가입 겸용) 여부. 응답 상태코드를 201/200으로 가른다
 */
public record SocialLoginResult(TokenPair tokens, Long userId, boolean newUser) {
}
