package com.kurly.common.security;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;

import java.util.Optional;

/**
 * 핸들러의 인가 규칙을 애노테이션에서 읽는다.
 *
 * <p>경로 패턴이 아니라 애노테이션을 유일한 근거로 삼는다. 경로 패턴 방식은
 * {@code /api/v1/auth/**} 같은 목록 하나로 인증이 필요한 엔드포인트까지 열어버릴 수 있다.
 * 메서드 표기가 클래스 표기보다 우선한다.
 */
public final class HandlerAuthorizationRules {

    private HandlerAuthorizationRules() {
    }

    public static Optional<HandlerAuthorizationRule> resolve(HandlerMethod handlerMethod) {
        Optional<HandlerAuthorizationRule> onMethod = read(handlerMethod.getMethod());
        if (onMethod.isPresent()) {
            return onMethod;
        }
        return read(handlerMethod.getBeanType());
    }

    private static Optional<HandlerAuthorizationRule> read(java.lang.reflect.AnnotatedElement element) {
        if (AnnotatedElementUtils.hasAnnotation(element, PublicApi.class)) {
            return Optional.of(HandlerAuthorizationRule.forPublicApi());
        }
        RequireRole requireRole = AnnotatedElementUtils.findMergedAnnotation(element, RequireRole.class);
        if (requireRole != null) {
            return Optional.of(HandlerAuthorizationRule.forRoles(requireRole.value()));
        }
        if (AnnotatedElementUtils.hasAnnotation(element, Authenticated.class)) {
            return Optional.of(HandlerAuthorizationRule.forAuthenticated());
        }
        return Optional.empty();
    }
}
