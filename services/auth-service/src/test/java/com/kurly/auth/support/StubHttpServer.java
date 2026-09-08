package com.kurly.auth.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 외부 HTTP 호출을 검증하기 위한 테스트용 스텁 서버.
 * 실제 소셜 제공자·user-service 없이 요청 형식과 응답 처리를 확인한다.
 */
public class StubHttpServer implements AutoCloseable {

    private final HttpServer server;
    private final List<Recorded> received = new ArrayList<>();

    public StubHttpServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("스텁 서버를 열지 못했습니다.", e);
        }
        server.start();
    }

    /** 지정 경로에서 고정 응답을 돌려준다. */
    public StubHttpServer stub(String path, int status, String body) {
        server.createContext(path, exchange -> {
            record(exchange);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        return this;
    }

    private void record(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        received.add(new Recorded(
                exchange.getRequestMethod(),
                exchange.getRequestURI().toString(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("Content-Type"),
                body));
    }

    public String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    public List<Recorded> received() {
        return received;
    }

    public Recorded lastReceived() {
        return received.getLast();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    public record Recorded(String method, String uri, String authorization, String contentType, String body) {
    }
}
