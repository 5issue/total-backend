package com.kurly.auth.infrastructure.client;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 외부 호출용 {@link RestClient} 생성 도우미.
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
