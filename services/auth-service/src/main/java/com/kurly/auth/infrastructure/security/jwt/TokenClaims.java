package com.kurly.auth.infrastructure.security.jwt;

import com.kurly.common.security.Role;
import com.kurly.common.security.TokenType;

import java.time.Instant;

public record TokenClaims(Long userId, Role role, TokenType tokenType, String jti, Instant expiresAt) {
}
