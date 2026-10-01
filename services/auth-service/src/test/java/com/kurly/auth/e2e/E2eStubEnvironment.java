package com.kurly.auth.e2e;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * E2E 시나리오를 <b>실제 환경 없이</b> 돌리기 위한 상태 보유 스텁.
 *
 * <p>⚠️ <b>이 스텁을 통과한다는 것은 제품이 정상이라는 뜻이 아니다.</b> 스텁은 우리가 기대하는
 * 계약을 그대로 구현한 것이므로, 실제 서비스가 그 계약을 어기면 스텁 테스트는 초록인 채로
 * 운영은 깨진다. 이 모드의 쓸모는 두 가지뿐이다.
 * <ul>
 *   <li>시나리오 코드 자체가 맞게 짜였는지 확인한다 — 어서션·경로·헤더가 의도대로인지</li>
 *   <li>토큰이 없는 CI에서도 테스트 골격이 썩지 않게 돌려 둔다</li>
 * </ul>
 * 제품 검증은 {@code E2E_TEST=true}로 실제 환경을 상대해야 한다.
 *
 * <p>회전·재사용 감지·멱등 같은 상태 전이를 재현해야 하므로 고정 응답으로는 부족하다.
 */
final class E2eStubEnvironment implements AutoCloseable {

    static final String TOKEN_A = "stub-access-token-a";
    static final String TOKEN_B = "stub-access-token-b";
    static final String INITIAL_REFRESH = "stub-refresh-0";
    static final long ADDRESS_A = 2001L;
    static final long ORDER_A = 17L;
    static final long PAYMENT_A = 31L;

    private final HttpServer server;

    /** 현재 유효한 refresh 값. 회전하면 바뀐다. */
    private volatile String activeRefresh = INITIAL_REFRESH;
    /** 세션 전체 폐기 여부. 재사용이 감지되면 켜진다. */
    private volatile boolean sessionRevoked = false;
    private volatile int rotation = 0;

    E2eStubEnvironment() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("스텁 서버를 열지 못했습니다.", e);
        }
        server.createContext("/api/v1/auth/refresh", this::refresh);
        server.createContext("/api/v1/users/me/profile", this::profile);
        server.createContext("/api/v1/users/me/addresses", this::addresses);
        server.createContext("/api/v1/orders/", this::order);
        server.createContext("/api/v1/payments/", this::receipt);
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** 시나리오마다 세션 상태를 되돌린다. 테스트가 서로의 순서에 의존하지 않게 한다. */
    void reset() {
        activeRefresh = INITIAL_REFRESH;
        sessionRevoked = false;
        rotation = 0;
    }

    // --- 라우트 ------------------------------------------------------------

    private void refresh(HttpExchange ex) throws IOException {
        String cookie = header(ex, "Cookie");
        String presented = cookieValue(cookie, "refresh_token");

        if (presented == null) {
            // 쿠키가 실리지 않은 경우. Path 때문에 빠지는 상황이 여기에 해당한다.
            send(ex, 401, error("인증이 필요합니다."), null, null);
            return;
        }
        if (sessionRevoked || !presented.equals(activeRefresh)) {
            // 이미 회전된 값을 다시 제시했다. 탈취와 구분되지 않으므로 전 세션을 폐기한다.
            sessionRevoked = true;
            send(ex, 401, error("유효하지 않거나 만료된 리프레시 토큰입니다. 다시 로그인해주세요."), null, null);
            return;
        }

        activeRefresh = "stub-refresh-" + (++rotation);
        String body = """
                {"status":"SUCCESS","message":"토큰이 성공적으로 재발급되었습니다.",\
                "data":{"accessToken":"%s","expiresIn":1800,"userId":1001},"error":null}"""
                .formatted(TOKEN_A);
        send(ex, 200, body,
                "refresh_token=" + activeRefresh + "; Path=/api/v1/auth/refresh; HttpOnly; Secure; SameSite=Strict",
                "no-store");
    }

    private void profile(HttpExchange ex) throws IOException {
        String token = bearer(ex);
        if (!TOKEN_A.equals(token) && !TOKEN_B.equals(token)) {
            // 사유를 드러내지 않는다. 서명 불일치인지 형식 오류인지 알려주면 단서가 된다.
            send(ex, 401, error("인증이 필요합니다."), null, null);
            return;
        }
        send(ex, 200, ok("{\"name\":\"스텁사용자\",\"defaultAddress\":null}"), null, null);
    }

    private void addresses(HttpExchange ex) throws IOException {
        String token = bearer(ex);
        String path = ex.getRequestURI().getPath();

        if (!TOKEN_A.equals(token) && !TOKEN_B.equals(token)) {
            send(ex, 401, error("인증이 필요합니다."), null, null);
            return;
        }
        // PATCH /api/v1/users/me/addresses/{id}/default
        if (path.endsWith("/default")) {
            boolean ownsIt = TOKEN_A.equals(token) && path.contains("/" + ADDRESS_A + "/");
            send(ex, ownsIt ? 200 : 404,
                    ownsIt ? ok("{\"addressId\":" + ADDRESS_A + "}") : error("배송지를 찾을 수 없습니다."),
                    null, null);
            return;
        }
        // 목록. 타인의 자원이 섞이면 단건 차단만으로는 의미가 없다.
        String list = TOKEN_A.equals(token)
                ? "{\"addresses\":[{\"addressId\":" + ADDRESS_A + ",\"addressName\":\"QA\"}]}"
                : "{\"addresses\":[]}";
        send(ex, 200, ok(list), null, null);
    }

    private void order(HttpExchange ex) throws IOException {
        String token = bearer(ex);
        boolean ownsIt = TOKEN_A.equals(token) && ex.getRequestURI().getPath().endsWith("/" + ORDER_A);
        send(ex, ownsIt ? 200 : 404,
                ownsIt ? ok("{\"orderId\":" + ORDER_A + ",\"status\":\"PAID\"}")
                        : error("주문을 찾을 수 없습니다."),
                null, null);
    }

    private void receipt(HttpExchange ex) throws IOException {
        String token = bearer(ex);
        boolean ownsIt = TOKEN_A.equals(token)
                && ex.getRequestURI().getPath().contains("/" + PAYMENT_A + "/");
        // 없는 결제와 타인의 결제가 같은 404여야 한다. 다르면 id를 훑어 존재를 알아낼 수 있다.
        send(ex, ownsIt ? 200 : 404,
                ownsIt ? ok("{\"paymentId\":" + PAYMENT_A + ",\"totalAmount\":32000}")
                        : error("결제 정보를 찾을 수 없습니다."),
                null, "no-store");
    }

    // --- 도구 --------------------------------------------------------------

    private static String ok(String data) {
        return "{\"status\":\"SUCCESS\",\"message\":\"조회되었습니다.\",\"data\":%s,\"error\":null}"
                .formatted(data);
    }

    private static String error(String message) {
        return "{\"status\":\"ERROR\",\"message\":\"%s\",\"data\":null,\"error\":\"UNAUTHORIZED\"}"
                .formatted(message);
    }

    private static String header(HttpExchange ex, String name) {
        return ex.getRequestHeaders().getFirst(name);
    }

    private static String bearer(HttpExchange ex) {
        String h = header(ex, "Authorization");
        return h != null && h.startsWith("Bearer ") ? h.substring(7).trim() : null;
    }

    private static String cookieValue(String cookieHeader, String name) {
        if (cookieHeader == null) {
            return null;
        }
        for (String part : cookieHeader.split(";")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) {
                return kv[1];
            }
        }
        return null;
    }

    private static void send(HttpExchange ex, int status, String body, String setCookie, String cacheControl)
            throws IOException {
        ex.getRequestBody().readAllBytes();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        if (setCookie != null) {
            ex.getResponseHeaders().add("Set-Cookie", setCookie);
        }
        if (cacheControl != null) {
            ex.getResponseHeaders().add("Cache-Control", cacheControl);
        }
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
