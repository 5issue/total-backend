package com.kurly.common.security;

import com.kurly.common.exception.UnauthorizedException;
import com.kurly.common.security.activity.SessionActivityRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.method.HandlerMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공개 엔드포인트에서의 활동 집계(설계서 1.6).
 *
 * <p>여기서 기록하지 않으면 유휴 판정에 구멍이 생긴다. 상품 조회처럼 공개인 경로만 오가는 동안
 * 기록이 남지 않아, 로그인한 사용자가 카탈로그를 한도 이상 둘러보다 갱신하면 유휴로 오판된다.
 */
@DisplayName("공개 엔드포인트의 활동 집계")
class PublicEndpointActivityUnitTest {

    static class ProbeController {
        @PublicApi
        @GetMapping("/open")
        String open() {
            return "open";
        }
    }

    private HandlerMethod publicHandler() throws Exception {
        return new HandlerMethod(new ProbeController(), ProbeController.class.getDeclaredMethod("open"));
    }

    private MockHttpServletRequest request(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/open");
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }

    @Test
    void 토큰이_있으면_공개_경로에서도_활동으로_센다() throws Exception {
        List<AuthenticatedPrincipal> recorded = new ArrayList<>();
        AuthenticationInterceptor interceptor = new AuthenticationInterceptor(
                token -> new AuthenticatedPrincipal(1001L, Role.USER), recorded::add);

        boolean proceed = interceptor.preHandle(
                request("Bearer valid-token"), new MockHttpServletResponse(), publicHandler());

        assertThat(proceed).isTrue();
        assertThat(recorded).extracting(AuthenticatedPrincipal::userId).containsExactly(1001L);
    }

    @Test
    void 토큰이_없으면_검증도_기록도_하지_않는다() throws Exception {
        AtomicBoolean verifierCalled = new AtomicBoolean(false);
        List<AuthenticatedPrincipal> recorded = new ArrayList<>();
        AuthenticationInterceptor interceptor = new AuthenticationInterceptor(token -> {
            verifierCalled.set(true);
            throw new IllegalStateException("호출되면 안 된다");
        }, recorded::add);

        boolean proceed = interceptor.preHandle(
                request(null), new MockHttpServletResponse(), publicHandler());

        assertThat(proceed).isTrue();
        assertThat(verifierCalled).isFalse();
        assertThat(recorded).isEmpty();
    }

    @Test
    void 토큰이_잘못돼도_공개_경로는_통과시킨다() throws Exception {
        // 만료된 토큰을 들고 있다는 이유로 공개 API가 401이 되면 안 된다.
        List<AuthenticatedPrincipal> recorded = new ArrayList<>();
        AuthenticationInterceptor interceptor = new AuthenticationInterceptor(token -> {
            throw new UnauthorizedException();
        }, recorded::add);

        boolean proceed = interceptor.preHandle(
                request("Bearer expired-token"), new MockHttpServletResponse(), publicHandler());

        assertThat(proceed).isTrue();
        assertThat(recorded).isEmpty();
    }

    @Test
    void 공개_경로에서는_주체를_요청에_담지_않는다() throws Exception {
        // 공개 엔드포인트의 기존 동작을 바꾸지 않는다. 기록만 한다.
        AuthenticationInterceptor interceptor = new AuthenticationInterceptor(
                token -> new AuthenticatedPrincipal(1001L, Role.USER), SessionActivityRecorder.NOOP);
        MockHttpServletRequest request = request("Bearer valid-token");

        interceptor.preHandle(request, new MockHttpServletResponse(), publicHandler());

        assertThat(request.getAttribute(AuthenticatedPrincipal.ATTRIBUTE)).isNull();
    }
}
