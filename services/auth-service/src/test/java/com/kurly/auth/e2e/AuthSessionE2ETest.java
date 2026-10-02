package com.kurly.auth.e2e;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static com.kurly.auth.e2e.E2eHttp.baseUrl;
import static com.kurly.auth.e2e.E2eHttp.get;
import static com.kurly.auth.e2e.E2eHttp.optional;
import static com.kurly.auth.e2e.E2eHttp.patch;
import static com.kurly.auth.e2e.E2eHttp.postWithCookie;
import static com.kurly.auth.e2e.E2eHttp.required;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 교차 시나리오 E2E-01 · E2E-07 (docs/QA_테스트케이스.md 9절).
 *
 * <p><b>이미 떠 있는 환경을 상대로 HTTP로 검증한다.</b> 서비스를 넘나드는 흐름이라 한 Spring
 * 컨텍스트로는 재현할 수 없다. dev처럼 한 호스트가 전 API를 서비스하면 {@code E2E_BASE_URL}만
 * 주고, 로컬처럼 포트가 갈리면 서비스별 변수로 덮어쓴다.
 *
 * <p>실행:
 * <pre>
 * E2E_TEST=true \
 * E2E_BASE_URL=https://dev.cloudyim.store \
 * E2E_TOKEN_A=&lt;계정A accessToken&gt; E2E_TOKEN_B=&lt;계정B accessToken&gt; \
 * ./gradlew :auth-service:test --tests '*AuthSessionE2ETest'
 * </pre>
 *
 * <p>준비물이 없는 시나리오는 <b>실패가 아니라 건너뜀</b>으로 처리한다. 토큰이 없다는 것은
 * 제품 결함이 아니라 준비 부족이고, 그것을 Fail로 적으면 보고서가 거짓이 된다.
 */
@DisplayName("E2E — 인증 세션과 자원 격리")
@EnabledIfEnvironmentVariable(named = "E2E_TEST", matches = "true|stub")
class AuthSessionE2ETest {

    /** {@code E2E_TEST=stub}일 때만 뜬다. 실제 환경을 상대할 때는 null이다. */
    private static E2eStubEnvironment stub;

    @BeforeAll
    static void startStubIfRequested() {
        if (!E2eHttp.stubMode()) {
            return;
        }
        stub = new E2eStubEnvironment();
        E2eHttp.override("E2E_BASE_URL", stub.baseUrl());
        E2eHttp.override("E2E_TOKEN_A", E2eStubEnvironment.TOKEN_A);
        E2eHttp.override("E2E_TOKEN_B", E2eStubEnvironment.TOKEN_B);
        E2eHttp.override("E2E_REFRESH_COOKIE", E2eStubEnvironment.INITIAL_REFRESH);
        E2eHttp.override("E2E_ADDRESS_A", String.valueOf(E2eStubEnvironment.ADDRESS_A));
        E2eHttp.override("E2E_ORDER_A", String.valueOf(E2eStubEnvironment.ORDER_A));
        E2eHttp.override("E2E_PAYMENT_A", String.valueOf(E2eStubEnvironment.PAYMENT_A));
    }

    @BeforeEach
    void resetStub() {
        if (stub != null) {
            // 시나리오가 서로의 순서에 의존하지 않게 세션 상태를 되돌린다.
            stub.reset();
        }
    }

    @AfterAll
    static void stopStub() {
        if (stub != null) {
            stub.close();
            stub = null;
        }
        E2eHttp.clearOverrides();
    }

    private static String authUrl() {
        return baseUrl("E2E_AUTH_URL");
    }

    private static String userUrl() {
        return baseUrl("E2E_USER_URL");
    }

    private static String orderUrl() {
        return baseUrl("E2E_ORDER_URL");
    }

    private static String paymentUrl() {
        return baseUrl("E2E_PAYMENT_URL");
    }

    @Nested
    @DisplayName("E2E-01 소셜 로그인 → refresh → 보호 API 호출")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class LoginToProtectedApi {

        /**
         * 해피 케이스.
         *
         * <p><b>소셜 로그인 구간은 자동화할 수 없다.</b> 제공자 화면에서 사람이 동의해야 하므로
         * 브라우저로 로그인한 뒤 받은 refresh 쿠키를 입력으로 받아 <b>그 이후 전 구간</b>을 검증한다.
         * 로그인 자체의 재현은 목 제공자를 쓰는 별도 절차를 따른다(docs/소셜로그인 로컬 재현.md).
         */
        @Test
        @Order(1)
        void refresh로_받은_토큰으로_보호_API까지_전_구간이_200이다() {
            String cookie = E2eHttp.refreshCookie();

            E2eHttp.Res refreshed = postWithCookie(authUrl() + "/api/v1/auth/refresh", cookie);

            assertThat(refreshed.status()).isEqualTo(200);
            assertThat(refreshed.data("accessToken")).isNotBlank();
            assertThat(refreshed.data("userId")).isNotBlank();
            // 본문에 access token이 실리므로 중간 캐시에 남아서는 안 된다.
            assertThat(refreshed.cacheControl()).contains("no-store");
            // 회전된 새 쿠키가 반드시 내려와야 한다. 안 내려오면 다음 갱신이 폐기된 토큰을 제시한다.
            assertThat(refreshed.setCookies())
                    .anyMatch(c -> c.startsWith("refresh_token=") && c.contains("HttpOnly"));

            String accessToken = refreshed.data("accessToken");
            E2eHttp.Res profile = get(userUrl() + "/api/v1/users/me/profile", accessToken);

            assertThat(profile.status()).isEqualTo(200);
        }

        @Test
        @Order(2)
        void 프론트가_Authorization_헤더를_붙이지_않으면_401이다() {
            // 운영에서 실제로 났던 상황이다. 메모리에만 있는 access token을 서버 사이드 렌더나
            // 새로고침 직후 경로에서 집어오지 못해 헤더 없이 호출된다.
            E2eHttp.Res res = get(userUrl() + "/api/v1/users/me/profile", null);

            assertThat(res.status()).isEqualTo(401);
            assertThat(res.error()).isEqualTo("UNAUTHORIZED");
        }

        @Test
        @Order(3)
        void 서명이_위조된_토큰을_제시하면_401이고_사유를_알려주지_않는다() {
            E2eHttp.Res res = get(userUrl() + "/api/v1/users/me/profile", "abc.def.ghi");

            assertThat(res.status()).isEqualTo(401);
            // 서명 불일치인지 형식 오류인지 구분해 주면 공격자에게 단서가 된다.
            assertThat(res.body()).doesNotContain("signature").doesNotContain("kid");
        }

        @Test
        @Order(4)
        void refresh_쿠키가_실리지_않으면_401이다() {
            // 쿠키 Path가 /api/v1/auth/refresh라 다른 경로에서는 실리지 않는다. 프론트가
            // credentials를 빼고 호출하면 이 상태가 된다.
            E2eHttp.Res res = postWithCookie(authUrl() + "/api/v1/auth/refresh", "unrelated=1");

            assertThat(res.status()).isEqualTo(401);
        }

        @Test
        @Order(5)
        void 폐기된_refresh_쿠키를_재사용하면_401이고_세션_전체가_무효화된다() {
            // ⚠️ 이 검증은 해당 계정의 세션을 전부 끊는다. 뒤 시나리오의 토큰까지 죽으므로
            // 명시적으로 켜야 돌아간다. 재사용 감지가 설계대로 동작하는지 확인하는 용도다.
            // 스텁은 시나리오마다 상태를 되돌리므로 안전하다. 실제 환경에서는 계정의 세션을
            // 전부 끊으므로 명시적으로 켜야 한다.
            assumeTrue(E2eHttp.stubMode() || "true".equals(optional("E2E_ALLOW_SESSION_DESTRUCTIVE")),
                    "세션을 파괴하는 검증이다. E2E_ALLOW_SESSION_DESTRUCTIVE=true 로만 실행한다");
            String cookie = E2eHttp.refreshCookie();

            E2eHttp.Res first = postWithCookie(authUrl() + "/api/v1/auth/refresh", cookie);
            assumeTrue(first.status() == 200, "1회차 갱신이 선행돼야 한다. 쿠키가 이미 만료됐다");

            // 회전됐으므로 같은 쿠키를 다시 쓰는 것은 탈취와 구분되지 않는다.
            E2eHttp.Res reused = postWithCookie(authUrl() + "/api/v1/auth/refresh", cookie);

            assertThat(reused.status()).isEqualTo(401);

            // 세션이 전부 폐기됐으므로 1회차에서 받은 새 쿠키도 더는 쓸 수 없다.
            String rotated = first.setCookies().stream()
                    .filter(c -> c.startsWith("refresh_token="))
                    .findFirst().map(c -> c.split(";")[0]).orElse(null);
            if (rotated != null) {
                assertThat(postWithCookie(authUrl() + "/api/v1/auth/refresh", rotated).status())
                        .isEqualTo(401);
            }
        }
    }

    @Nested
    @DisplayName("E2E-07 계정B로 계정A의 전 자원 순회 — 전부 차단")
    class CrossAccountResourceAccess {

        /** 차단은 404 또는 403이다. 404가 더 안전하다 — 자원의 존재 여부조차 알려주지 않는다. */
        private void assertBlocked(E2eHttp.Res res) {
            assertThat(res.status())
                    .as("타인 자원 접근은 차단돼야 한다. 응답=%s %s", res.status(), res.body())
                    .isIn(403, 404);
        }

        @Test
        void 본인_자원은_정상_조회된다() {
            String tokenA = required("E2E_TOKEN_A");

            assertThat(get(userUrl() + "/api/v1/users/me/addresses", tokenA).status()).isEqualTo(200);
            assertThat(get(userUrl() + "/api/v1/users/me/profile", tokenA).status()).isEqualTo(200);
        }

        @Test
        void 타인의_배송지를_기본으로_바꾸려_하면_차단된다() {
            String tokenB = required("E2E_TOKEN_B");
            String addressA = required("E2E_ADDRESS_A");

            // 경로의 식별자만 바꿔 넣는 가장 단순한 공격이다. 서버가 토큰 주체와 대조해야 막힌다.
            assertBlocked(patch(
                    userUrl() + "/api/v1/users/me/addresses/" + addressA + "/default", tokenB, null));
        }

        @Test
        void 타인의_주문_상세를_조회하려_하면_차단된다() {
            String tokenB = required("E2E_TOKEN_B");
            String orderA = required("E2E_ORDER_A");

            assertBlocked(get(orderUrl() + "/api/v1/orders/" + orderA, tokenB));
        }

        @Test
        void 타인의_결제_영수증을_조회하려_하면_404다() {
            String tokenB = required("E2E_TOKEN_B");
            String paymentA = required("E2E_PAYMENT_A");

            E2eHttp.Res res = get(paymentUrl() + "/api/v1/payments/" + paymentA + "/receipt", tokenB);

            assertThat(res.status()).isEqualTo(404);
        }

        @Test
        void 없는_결제와_타인의_결제가_같은_404여야_한다() {
            String tokenB = required("E2E_TOKEN_B");
            String paymentA = required("E2E_PAYMENT_A");

            E2eHttp.Res missing = get(paymentUrl() + "/api/v1/payments/99999999/receipt", tokenB);
            E2eHttp.Res othersOwn = get(paymentUrl() + "/api/v1/payments/" + paymentA + "/receipt", tokenB);

            // 응답이 다르면 id를 훑어 어느 결제가 존재하는지 알아낼 수 있다.
            assertThat(missing.status()).isEqualTo(othersOwn.status());
            assertThat(missing.error()).isEqualTo(othersOwn.error());
        }

        @Test
        void 목록_조회에_타인의_자원이_섞이지_않는다() {
            String tokenB = required("E2E_TOKEN_B");
            String addressA = required("E2E_ADDRESS_A");

            E2eHttp.Res addresses = get(userUrl() + "/api/v1/users/me/addresses", tokenB);

            assertThat(addresses.status()).isEqualTo(200);
            // 단건 차단만 막고 목록에서 새면 의미가 없다.
            assertThat(addresses.bodyContains("\"addressId\":" + addressA)).isFalse();
        }
    }
}
