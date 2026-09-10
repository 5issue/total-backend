package com.kurly.payment.infrastructure.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 들어온 요청의 {@code Authorization} 헤더를 나가는 호출에 그대로 실어 보낸다.
 *
 * <p>동기 호출은 JWT를 전파하고 각 서비스가 재검증하는 것이 설계 기준이다(인증인가_설계서 3.4).
 * 주문 조회는 소유자 확인이 걸린 내부 API라 토큰 없이는 401이 된다.
 *
 * <p><b>요청 스코프 밖에서는 아무것도 붙이지 않는다.</b> 배치·메시지 소비처럼 사용자 토큰이 없는
 * 흐름에서 호출되면 상대가 401로 거절한다. 그 경우 토큰을 지어내는 대신 실패하게 두는 것이 옳다 —
 * 사용자 없는 흐름이 사용자 권한으로 동작하면 안 된다.
 */
@Slf4j
public class AuthorizationForwarder implements ClientHttpRequestInterceptor {

    @Override
    public org.springframework.http.client.ClientHttpResponse intercept(
            org.springframework.http.HttpRequest request, byte[] body,
            org.springframework.http.client.ClientHttpRequestExecution execution) throws java.io.IOException {

        currentAuthorization().ifPresentOrElse(
                token -> request.getHeaders().set(HttpHeaders.AUTHORIZATION, token),
                () -> log.warn("전파할 Authorization 헤더가 없습니다. 요청 스코프 밖에서 호출됐습니다: {}",
                        request.getURI().getPath()));
        return execution.execute(request, body);
    }

    private java.util.Optional<String> currentAuthorization() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(
                attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION));
    }
}
