package com.kurly.auth.e2e;

import org.junit.jupiter.api.Assumptions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * E2E 테스트용 최소 HTTP 도구.
 *
 * <p><b>Spring 컨텍스트를 띄우지 않는다.</b> 검증 대상은 이미 떠 있는 환경(dev 또는 로컬 기동)이며,
 * 테스트가 스스로 서비스를 띄우면 서비스 간 흐름을 확인할 수 없다.
 *
 * <p>대상 주소는 환경변수로 받는다. dev처럼 한 호스트가 전 API를 서비스하면 {@code E2E_BASE_URL}
 * 하나로 끝나고, 로컬처럼 서비스마다 포트가 다르면 서비스별 변수로 덮어쓴다.
 */
final class E2eHttp {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            // 리다이렉트를 따라가지 않는다. 302 자체가 검증 대상인 구간이 있다.
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private E2eHttp() {
    }

    /**
     * 값이 없으면 테스트를 실패가 아니라 중단으로 처리한다. 준비물 부족은 결함이 아니다.
     *
     * <p><b>치환하지 않은 플레이스홀더도 걸러낸다.</b> {@code <ID>} 같은 값은 HTTP 헤더 검증은
     * 통과해 버려서, 그대로 URL에 실려 엉뚱한 404를 만든다. 그 404가 제품 결함으로 기록되면
     * 보고서가 거짓이 되므로, 조용히 흘려보내지 않고 여기서 멈춘다.
     */
    /**
     * 스텁 모드에서 주입하는 값. 환경변수보다 우선한다.
     *
     * <p>스텁 서버는 떠 있는 포트가 매번 달라 환경변수로는 넘길 수 없다. {@code System.getenv}는
     * 수정할 수 없으므로 별도 통로를 둔다.
     */
    private static final java.util.Map<String, String> OVERRIDES = new java.util.concurrent.ConcurrentHashMap<>();

    static boolean stubMode() {
        return "stub".equals(System.getenv("E2E_TEST"));
    }

    static void override(String name, String value) {
        OVERRIDES.put(name, value);
    }

    static void clearOverrides() {
        OVERRIDES.clear();
    }

    static String required(String name) {
        String v = OVERRIDES.getOrDefault(name, System.getenv(name));
        Assumptions.assumeTrue(v != null && !v.isBlank(),
                () -> "환경변수 " + name + "가 없어 건너뛴다. 준비물은 docs/QA_테스트케이스.md 0절 참조");
        Assumptions.assumeFalse(v.indexOf('<') >= 0 || v.indexOf('>') >= 0,
                () -> "환경변수 " + name + "에 플레이스홀더가 그대로 있다: " + v
                        + " — 실제 값으로 바꿔야 한다");
        Assumptions.assumeTrue(isAscii(v),
                () -> "환경변수 " + name + "에 ASCII가 아닌 문자가 있다: " + v
                        + " — 값을 잘못 붙여넣었는지 확인한다");
        return v;
    }

    private static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 0x7e || s.charAt(i) < 0x20) {
                return false;
            }
        }
        return true;
    }

    /**
     * refresh 쿠키 값을 읽는다.
     *
     * <p>devtools에서 복사할 때 <b>값만</b> 가져오는 경우가 많아, {@code =}가 없으면 기본 쿠키
     * 이름을 붙여 준다. 이름을 바꿔 운영하는 환경이라면 {@code name=value} 형태로 넣는다.
     */
    static String refreshCookie() {
        String v = required("E2E_REFRESH_COOKIE");
        return v.contains("=") ? v : "refresh_token=" + v;
    }

    static String optional(String name) {
        String v = OVERRIDES.getOrDefault(name, System.getenv(name));
        return v == null || v.isBlank() ? null : v;
    }

    /** 서비스별 주소. 개별 변수가 없으면 공통 base URL을 쓴다. */
    static String baseUrl(String serviceEnvName) {
        String specific = optional(serviceEnvName);
        return specific != null ? stripTrailingSlash(specific) : stripTrailingSlash(required("E2E_BASE_URL"));
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    static Res get(String url, String token) {
        return send(request(url, token).GET());
    }

    static Res post(String url, String token, String jsonBody, String... extraHeaders) {
        HttpRequest.Builder b = request(url, token)
                .header("Content-Type", "application/json")
                .POST(jsonBody == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(jsonBody));
        for (int i = 0; i + 1 < extraHeaders.length; i += 2) {
            b.header(extraHeaders[i], extraHeaders[i + 1]);
        }
        return send(b);
    }

    static Res patch(String url, String token, String jsonBody) {
        return send(request(url, token)
                .header("Content-Type", "application/json")
                .method("PATCH", jsonBody == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(jsonBody)));
    }

    /** refresh는 토큰이 아니라 쿠키로 인증한다(설계서 1.4). */
    static Res postWithCookie(String url, String cookie) {
        return send(HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Cookie", cookie)
                .POST(HttpRequest.BodyPublishers.noBody()));
    }

    private static HttpRequest.Builder request(String url, String token) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15));
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return b;
    }

    private static Res send(HttpRequest.Builder builder) {
        try {
            HttpResponse<String> r = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Res(r.statusCode(), r.body(), r.headers().allValues("Set-Cookie"),
                    r.headers().firstValue("Cache-Control").orElse(null));
        } catch (Exception e) {
            throw new IllegalStateException("요청 실패: " + builder.build().uri(), e);
        }
    }

    record Res(int status, String body, java.util.List<String> setCookies, String cacheControl) {

        /** {@code ApiResponse.data} 아래의 필드. 없으면 null이다. */
        String data(String field) {
            JsonNode n = json().path("data").path(field);
            return n.isMissingNode() || n.isNull() ? null : n.asString();
        }

        String error() {
            JsonNode n = json().path("error");
            return n.isMissingNode() || n.isNull() ? null : n.asString();
        }

        JsonNode json() {
            try {
                return MAPPER.readTree(body);
            } catch (Exception e) {
                throw new IllegalStateException("JSON이 아닌 응답: status=" + status + " body=" + body, e);
            }
        }

        boolean bodyContains(String s) {
            return body != null && body.contains(s);
        }
    }
}
