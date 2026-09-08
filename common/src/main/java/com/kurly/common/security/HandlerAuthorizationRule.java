package com.kurly.common.security;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 핸들러 하나의 인가 규칙.
 *
 * @param allowedRoles 비어 있으면 역할을 가리지 않는다
 */
public record HandlerAuthorizationRule(boolean publicAccess, Set<Role> allowedRoles) {

    public HandlerAuthorizationRule {
        allowedRoles = Set.copyOf(allowedRoles);
    }

    public static HandlerAuthorizationRule forPublicApi() {
        return new HandlerAuthorizationRule(true, Set.of());
    }

    public static HandlerAuthorizationRule forAuthenticated() {
        return new HandlerAuthorizationRule(false, Set.of());
    }

    public static HandlerAuthorizationRule forRoles(Role... roles) {
        return new HandlerAuthorizationRule(false, Arrays.stream(roles).collect(Collectors.toSet()));
    }

    public boolean permits(Role role) {
        return allowedRoles.isEmpty() || allowedRoles.contains(role);
    }
}
