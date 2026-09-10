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

    /**
     * 역할을 가리는 규칙. <b>빈 배열은 거부한다.</b> 빈 집합은 "역할을 가리지 않는다"로 해석되므로
     * {@code @RequireRole}이 조용히 {@code @Authenticated}로 격하된다. 설정 실수를 통과시키지
     * 않는다는 점에서 기동 시 미분류 핸들러를 잡아 세우는 것과 같은 취지다.
     */
    public static HandlerAuthorizationRule forRoles(Role... roles) {
        if (roles == null || roles.length == 0) {
            throw new IllegalArgumentException(
                    "@RequireRole에는 역할을 하나 이상 지정해야 합니다. 역할 제한이 필요 없으면 @Authenticated를 사용하세요.");
        }
        return new HandlerAuthorizationRule(false, Arrays.stream(roles).collect(Collectors.toSet()));
    }

    public boolean permits(Role role) {
        return allowedRoles.isEmpty() || allowedRoles.contains(role);
    }
}
