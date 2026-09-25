package com.kurly.common.security;

import com.kurly.common.exception.ForbiddenException;
import com.kurly.common.security.activity.SessionActivityRecorder;
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
    private final SessionActivityRecorder activityRecorder;

    /** 활동 기록이 필요 없는 환경(테스트 등)에서 쓰는 생성자. */
    public AuthenticationInterceptor(JwtVerifier jwtVerifier) {
        this(jwtVerifier, SessionActivityRecorder.NOOP);
    }

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
            recordIfAuthenticated(request);
            return true;
        }

        AuthenticatedPrincipal principal = jwtVerifier.verify(extractBearerToken(request));

        if (!rule.permits(principal.role())) {
            log.debug("역할 부족: userId={}, role={}, allowed={}",
                    principal.userId(), principal.role(), rule.allowedRoles());
            throw new ForbiddenException();
        }

        request.setAttribute(AuthenticatedPrincipal.ATTRIBUTE, principal);

        // 인증에 성공한 요청만 활동으로 센다. 공개 엔드포인트는 주체가 없어 여기 도달하지 않는다.
        activityRecorder.record(principal);
        return true;
    }

    /**
     * 공개 엔드포인트에서도 토큰이 있으면 활동으로 센다(설계서 1.6).
     *
     * <p><b>없으면 유휴 판정에 구멍이 생긴다.</b> 상품 조회처럼 공개인 경로만 오가는 동안에는
     * 기록이 남지 않아, 로그인한 사용자가 카탈로그를 한도 이상 둘러보다 갱신하면 유휴로 오판된다.
     *
     * <p><b>검증 실패는 무시한다.</b> 공개 경로는 토큰이 없거나 잘못돼도 통과시켜야 한다.
     * 여기서 던지면 만료된 토큰을 들고 있다는 이유로 공개 API가 401이 된다.
     *
     * <p>주체를 요청 attribute에 담지는 않는다. 공개 엔드포인트의 동작을 바꾸지 않기 위함이다.
     */
    private void recordIfAuthenticated(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)) {
            return;
        }
        try {
            activityRecorder.record(jwtVerifier.verify(header.substring(BEARER_PREFIX.length()).trim()));
        } catch (Exception e) {
            log.trace("공개 경로의 토큰 검증 실패. 활동으로 세지 않고 통과시킨다", e);
        }
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
