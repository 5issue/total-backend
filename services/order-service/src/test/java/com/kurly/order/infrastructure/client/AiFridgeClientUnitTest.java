package com.kurly.order.infrastructure.client;

import com.kurly.order.application.FridgeClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제로 나가는 요청을 검증한다.
 *
 * <p><b>호출부를 목으로 대체한 테스트로는 이 계층이 덮이지 않는다.</b> 운영에서 AI가 422로
 * 거부한 원인이 본문에 {@code unit}이 없다는 것이었는데, 그 종류의 결함은 직렬화 결과를
 * 직접 보지 않으면 드러나지 않는다.
 */
@DisplayName("AI 냉장고 적재 요청")
class AiFridgeClientUnitTest {

    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private volatile int status = 200;

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            path.set(exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();

        // 관리자 토큰은 요청 스코프에서 꺼내 전파한다. 스코프가 없으면 호출 자체가 막힌다.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer admin-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void stopStub() {
        RequestContextHolder.resetRequestAttributes();
        server.stop(0);
    }

    private AiFridgeClient client() {
        return new AiFridgeClient(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "/api/v1/internal/fridge/items",
                Duration.ofSeconds(2), Duration.ofSeconds(5));
    }

    @Test
    void 명세대로_snake_case로_직렬화한다() {
        client().addItems(5L, List.of(new FridgeClient.FridgeItem(101L, 2)));

        assertThat(body.get())
                .contains("\"user_id\":5")
                .contains("\"product_id\":101")
                .contains("\"quantity\":2");
        // 이 서비스의 기본은 camelCase다. 섞이면 AI가 422로 거부한다.
        assertThat(body.get()).doesNotContain("userId").doesNotContain("productId");
    }

    @Test
    void unit은_AI_필수_필드라_고정값을_보낸다() {
        client().addItems(5L, List.of(new FridgeClient.FridgeItem(101L, 1)));

        // 주문 도메인에 단위가 없어 지어낼 수 없다. 빼면 422가 된다(운영에서 실제로 났다).
        assertThat(body.get()).contains("\"unit\":\"개\"");
    }

    @Test
    void expires_at은_선택값이라_보내지_않는다() {
        client().addItems(5L, List.of(new FridgeClient.FridgeItem(101L, 1)));

        assertThat(body.get()).doesNotContain("expires_at");
    }

    @Test
    void 관리자_토큰을_그대로_전파한다() {
        client().addItems(5L, List.of(new FridgeClient.FridgeItem(101L, 1)));

        assertThat(authorization.get()).isEqualTo("Bearer admin-token");
        assertThat(path.get()).isEqualTo("/api/v1/internal/fridge/items");
    }

    @Test
    void 품목이_없으면_호출하지_않는다() {
        client().addItems(5L, List.of());

        assertThat(body.get()).isNull();
    }

    @Test
    void AI가_실패를_돌려주면_예외로_올린다() {
        status = 422;

        assertThatThrownBy(() -> client().addItems(5L, List.of(new FridgeClient.FridgeItem(101L, 1))))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void 주소가_비어_있으면_호출_전에_막는다() {
        AiFridgeClient disabled = new AiFridgeClient(
                "", "/api/v1/internal/fridge/items", Duration.ofSeconds(2), Duration.ofSeconds(5));

        // AI_BASE_URL 미주입. 기동은 막지 않되 호출은 실패시켜 fridgeSynced=false로 드러낸다.
        assertThatThrownBy(() -> disabled.addItems(5L, List.of(new FridgeClient.FridgeItem(101L, 1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI_BASE_URL");
    }

    @Test
    void 요청_스코프_밖에서는_토큰을_지어내지_않고_실패한다() {
        RequestContextHolder.resetRequestAttributes();

        // 배치·메시지 소비처럼 사용자 없는 흐름이 관리자 권한으로 나가면 안 된다.
        assertThatThrownBy(() -> client().addItems(5L, List.of(new FridgeClient.FridgeItem(101L, 1))))
                .isInstanceOf(IllegalStateException.class);
    }
}
