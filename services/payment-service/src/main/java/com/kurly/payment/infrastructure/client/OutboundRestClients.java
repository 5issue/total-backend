package com.kurly.payment.infrastructure.client;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 외부 호출용 {@link RestClient} 생성 도우미.
 *
 * <p>타임아웃을 반드시 건다. 걸지 않으면 상대 서비스가 응답하지 않을 때 우리 스레드가 무한정 묶여
 * 결제와 무관한 요청까지 밀린다.
 */
public final class OutboundRestClients {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private OutboundRestClients() {
    }

    public static RestClient.Builder builder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().requestFactory(factory);
    }
}
