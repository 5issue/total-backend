package com.kurly.payment.support;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 외부 서비스 대역. 실제 HTTP를 주고받아야 헤더 전파·상태 코드 처리 같은 것이 진짜로 검증된다.
 * 목으로는 RestClient가 실제로 무엇을 보내는지 알 수 없다.
 */
public class StubHttpServer implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, Recorded> received = new HashMap<>();

    public StubHttpServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.start();
        } catch (IOException e) {
            throw new IllegalStateException("대역 서버를 띄우지 못했습니다.", e);
        }
    }

    /** 경로별 응답을 등록한다. */
    public StubHttpServer stub(String path, int status, String body) {
        server.createContext(path, exchange -> {
            received.put(path, new Recorded(
                    exchange.getRequestMethod(),
                    exchange.getRequestHeaders().getFirst("Authorization")));
            byte[] payload = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, payload.length == 0 ? -1 : payload.length);
            if (payload.length > 0) {
                exchange.getResponseBody().write(payload);
            }
            exchange.close();
        });
        return this;
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public Recorded received(String path) {
        return received.get(path);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    public record Recorded(String method, String authorization) {
    }
}
