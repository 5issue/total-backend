package com.kurly.payment.e2e;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 결제·주문 E2E를 <b>실제 환경 없이</b> 돌리기 위한 상태 보유 스텁.
 *
 * <p>⚠️ <b>이 스텁을 통과한다는 것은 제품이 정상이라는 뜻이 아니다.</b> 우리가 기대하는 계약을
 * 그대로 구현한 것이므로, 실제 서비스가 계약을 어기면 스텁은 초록인 채로 운영이 깨진다.
 * 제품 검증은 {@code E2E_TEST=true}로 실제 환경을 상대해야 한다.
 *
 * <p>멱등 재생과 취소 1회성은 상태가 있어야 재현되므로 고정 응답으로는 부족하다.
 */
final class E2eStubEnvironment implements AutoCloseable {

    static final String TOKEN_A = "stub-access-token-a";
    static final String TOKEN_B = "stub-access-token-b";
    static final long ORDER_PENDING = 18L;
    static final long ORDER_PAID = 19L;
    static final String PAYMENT_KEY = "stub-payment-key";
    static final long AMOUNT = 32000L;

    private static final Pattern AMOUNT_FIELD = Pattern.compile("\"amount\"\\s*:\\s*(-?[0-9.]+)");
    private static final Pattern ORDER_FIELD = Pattern.compile("\"orderId\"\\s*:\\s*([0-9]+)");
    private static final Pattern REASON_CODE = Pattern.compile("\"reasonCode\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern REASON_DETAIL = Pattern.compile("\"reasonDetail\"\\s*:\\s*\"([^\"]*)\"");

    private final HttpServer server;

    /** 주문 상태. 결제 승인·취소로 전이한다. */
    private final Map<Long, String> orderStatus = new ConcurrentHashMap<>();
    /** 멱등키 → 이미 돌려준 응답 본문. 재요청이면 PG를 다시 부르지 않는다. */
    private final Map<String, String> idempotent = new ConcurrentHashMap<>();
    private volatile long paymentSeq = 500L;

    E2eStubEnvironment() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("스텁 서버를 열지 못했습니다.", e);
        }
        server.createContext("/api/v1/payments/checkout", this::checkout);
        server.createContext("/api/v1/orders/", this::orders);
        server.start();
        reset();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void reset() {
        orderStatus.put(ORDER_PENDING, "PAYMENT_PENDING");
        orderStatus.put(ORDER_PAID, "PAID");
        idempotent.clear();
    }

    // --- 결제 승인 ---------------------------------------------------------

    private void checkout(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String key = ex.getRequestHeaders().getFirst("Idempotency-Key");
        String token = bearer(ex);

        // 1) 멱등키 누락은 본문을 보기 전에 거른다. 재시도가 이중 승인이 될 수 있는 요청이다.
        if (key == null || key.isBlank()) {
            send(ex, 400, error("Idempotency-Key는 필수입니다.", "COMMON400"), null);
            return;
        }
        // 2) 재요청이면 저장된 응답을 그대로 돌려준다. PG를 다시 부르지 않는다.
        String replayed = idempotent.get(key);
        if (replayed != null) {
            send(ex, 200, replayed, "no-store");
            return;
        }
        // 3) 소유권. 타인 주문은 존재 여부조차 알려주지 않는다.
        if (!TOKEN_A.equals(token)) {
            send(ex, 404, error("주문을 찾을 수 없습니다.", "COMMON404"), null);
            return;
        }
        // 4) 금액 형식. 소수를 잘라 받으면 승인 금액과 요청 금액이 조용히 어긋난다.
        String raw = group(AMOUNT_FIELD, body);
        if (raw == null || raw.contains(".")) {
            send(ex, 400, error("결제 금액 형식이 올바르지 않습니다.", "COMMON400"), null);
            return;
        }
        long amount = Long.parseLong(raw);
        if (amount <= 0) {
            send(ex, 400, error("결제 금액은 0보다 커야 합니다.", "COMMON400"), null);
            return;
        }
        long orderId = Long.parseLong(group(ORDER_FIELD, body));
        // 5) 금액 위변조. 클라이언트가 보낸 금액을 믿으면 1원 결제가 가능해진다.
        if (amount != AMOUNT) {
            send(ex, 400, error("결제 금액이 주문 금액과 일치하지 않습니다.", "PAY_AMOUNT_MISMATCH"), null);
            return;
        }
        // 6) 이미 결제된 주문. 멱등키가 달라도 중복 승인은 막아야 한다.
        if ("PAID".equals(orderStatus.get(orderId))) {
            send(ex, 409, error("이미 결제가 완료된 주문입니다.", "PAY_ALREADY_PAID"), null);
            return;
        }

        long paymentId = ++paymentSeq;
        String response = """
                {"status":"SUCCESS","message":"결제가 성공적으로 승인 및 완료되었습니다.",\
                "data":{"paymentId":%d,"paymentCompletedAt":"2026-10-01T00:00:00Z",\
                "receiptUrl":"https://stub/receipt/%d","paymentStatus":"SUCCESS"},"error":null}"""
                .formatted(paymentId, paymentId);
        idempotent.put(key, response);
        // 결제가 주문에 인계되어 PAID로 전이한다. 이 전이가 없으면 돈만 빠져나간 상태다.
        orderStatus.put(orderId, "PAID");
        send(ex, 200, response, "no-store");
    }

    // --- 주문 조회·취소 ----------------------------------------------------

    private void orders(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String token = bearer(ex);
        String path = ex.getRequestURI().getPath();

        if (path.endsWith("/cancel")) {
            cancel(ex, body, token, idIn(path, "/cancel"));
            return;
        }
        long orderId = Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
        if (!TOKEN_A.equals(token) || !orderStatus.containsKey(orderId)) {
            send(ex, 404, error("주문을 찾을 수 없습니다.", "COMMON404"), null);
            return;
        }
        send(ex, 200, ok("{\"orderId\":%d,\"status\":\"%s\"}".formatted(orderId, orderStatus.get(orderId))), null);
    }

    private void cancel(HttpExchange ex, String body, String token, long orderId) throws IOException {
        // 소유권을 가장 먼저 본다. 남의 주문을 취소할 수 있으면 다른 검증은 의미가 없다.
        if (!TOKEN_A.equals(token) || !orderStatus.containsKey(orderId)) {
            send(ex, 404, error("주문을 찾을 수 없습니다.", "COMMON404"), null);
            return;
        }
        String code = group(REASON_CODE, body);
        String detail = group(REASON_DETAIL, body);

        // 입력 검증을 업무 상태 검증보다 먼저 한다. 잘못된 입력에 상태 오류를 돌려주면 혼동된다.
        if (code == null || !code.matches("CNL0[1-5]|CNL99")) {
            send(ex, 400, error("취소 사유 코드가 올바르지 않습니다.", "ORD_INVALID_REASON_CODE"), null);
            return;
        }
        if ("CNL99".equals(code) && (detail == null || detail.isBlank())) {
            send(ex, 400, error("취소 사유 상세는 필수입니다.", "ORD_INVALID_REASON_DETAIL"), null);
            return;
        }
        String status = orderStatus.get(orderId);
        if (!"PAID".equals(status)) {
            // 이미 취소된 건도 여기로 떨어진다. 두 번 환불되면 그대로 손실이다.
            send(ex, 409, error("취소할 수 없는 주문 상태입니다.", "ORD_INVALID_STATUS"), null);
            return;
        }
        orderStatus.put(orderId, "CANCELED");
        send(ex, 200, ok("{\"orderId\":%d,\"status\":\"CANCELED\"}".formatted(orderId)), null);
    }

    // --- 도구 --------------------------------------------------------------

    private static long idIn(String path, String suffix) {
        String head = path.substring(0, path.length() - suffix.length());
        return Long.parseLong(head.substring(head.lastIndexOf('/') + 1));
    }

    private static String group(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1) : null;
    }

    private static String ok(String data) {
        return "{\"status\":\"SUCCESS\",\"message\":\"처리되었습니다.\",\"data\":%s,\"error\":null}".formatted(data);
    }

    private static String error(String message, String code) {
        return "{\"status\":\"ERROR\",\"message\":\"%s\",\"data\":null,\"error\":\"%s\"}".formatted(message, code);
    }

    private static String bearer(HttpExchange ex) {
        String h = ex.getRequestHeaders().getFirst("Authorization");
        return h != null && h.startsWith("Bearer ") ? h.substring(7).trim() : null;
    }

    private static void send(HttpExchange ex, int status, String body, String cacheControl) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
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
