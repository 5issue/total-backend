package com.kurly.auth.application.dto;

public record SocialLoginResult(TokenPair tokens, Long userId) {
}
