package com.kurly.common.security;

import com.kurly.common.exception.UnauthorizedException;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@code @AuthPrincipal AuthenticatedPrincipal me} 파라미터를 주입한다.
 * 값은 인터셉터가 요청 attribute에 담아둔 것이며, 요청 스코프를 벗어나지 않는다.
 */
public class AuthPrincipalArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthPrincipal.class)
                && AuthenticatedPrincipal.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        Object principal = webRequest.getAttribute(
                AuthenticatedPrincipal.ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (principal == null) {
            // @PublicApi 핸들러에서 주체를 요구한 경우다. 설정 실수이므로 통과시키지 않는다.
            throw new UnauthorizedException();
        }
        return principal;
    }
}
