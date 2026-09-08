package com.kurly.common.security;

import com.kurly.common.exception.ForbiddenException;
import com.kurly.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 요청의 access token을 검증하고 역할 인가를 강제한다.
 *
 * <p>여기서 던지는 예외는 {@code DispatcherServlet} 안에서 발생하므로
 * {@code GlobalExceptionHandler}가 처리해 공통 {@code ApiResponse} 포맷이 유지된다.
 *
 * <p>소유권 인가(ABAC)는 여기서 하지 않는다. 진입부는 리소스 소유자를 모르므로
 * 각 서비스의 서비스 계층 책임이다(인증인가_설계서 2.3).
 */
@Slf4j
@RequiredArgsConstructor
public class AuthenticationInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtVerifier jwtVerifier;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // CORS 프리플라이트는 자격증명을 싣지 않는다.
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }
        // 컨트롤러 핸들러가 아닌 요청(정적 리소스, 프레임워크 핸들러)은 대상이 아니다.
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 애노테이션이 없으면 기동 시 HandlerAuthorizationAuditor가 이미 막았어야 한다.
        // 그럼에도 도달했다면 안전한 쪽(인증 필요)으로 처리한다.
        HandlerAuthorizationRule rule = HandlerAuthorizationRules.resolve(handlerMethod)
                .orElseGet(HandlerAuthorizationRule::forAuthenticated);

        if (rule.publicAccess()) {
            return true;
        }

        AuthenticatedPrincipal principal = jwtVerifier.verify(extractBearerToken(request));

        if (!rule.permits(principal.role())) {
            log.debug("역할 부족: userId={}, role={}, allowed={}",
                    principal.userId(), principal.role(), rule.allowedRoles());
            throw new ForbiddenException();
        }

        request.setAttribute(AuthenticatedPrincipal.ATTRIBUTE, principal);
        return true;
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)) {
            throw new UnauthorizedException();
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            throw new UnauthorizedException();
        }
        return token;
    }
}
