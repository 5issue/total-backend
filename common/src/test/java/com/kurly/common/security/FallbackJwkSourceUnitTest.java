package com.kurly.common.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackJwkSourceUnitTest {

    private JWK fallbackKey;
    private JWKSelector anyKey;

    @BeforeEach
    void setUp() throws Exception {
        fallbackKey = new ECKeyGenerator(Curve.P_256)
                .keyID("fallback-kid")
                .algorithm(JWSAlgorithm.ES256)
                .generate();
        anyKey = new JWKSelector(new JWKMatcher.Builder().build());
    }

    private static JWKSource<SecurityContext> returning(List<JWK> keys) {
        return (selector, context) -> keys;
    }

    private static final Duration WINDOW = Duration.ofMinutes(10);

    /** 테스트가 시간을 직접 밀 수 있도록 하는 시계. 실제 대기 없이 창 만료를 재현한다. */
    private static final class MovableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));

        void advance(Duration amount) {
            now.updateAndGet(instant -> instant.plus(amount));
        }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }

    private FallbackJwkSource source(JWKSource<SecurityContext> primary, Clock clock) {
        return new FallbackJwkSource(primary, fallbackKey, WINDOW, clock);
    }

    private static JWKSource<SecurityContext> failing() {
        return (selector, context) -> {
            throw new IllegalStateException("원격 JWKS 연결 실패");
        };
    }

    @Nested
    @DisplayName("원격 우선")
    class PrimaryTest {

        @Test
        void 원격_결과가_있으면_그대로_쓴다() throws Exception {
            JWK remoteKey = new ECKeyGenerator(Curve.P_256).keyID("remote-kid").generate();
            FallbackJwkSource source = new FallbackJwkSource(returning(List.of(remoteKey)), fallbackKey, WINDOW);

            assertThat(source.get(anyKey, null))
                    .extracting(JWK::getKeyID)
                    .containsExactly("remote-kid");
        }
    }

    @Nested
    @DisplayName("폐기된 키")
    class RevokedKeyTest {

        @Test
        void 원격이_정상_응답했는데_키가_없으면_폴백하지_않는다() {
            // 원격이 응답했는데 그 kid가 없다는 것은 키가 폐기됐다는 뜻이다.
            // 여기서 폴백하면 유출된 개인키로 서명한 토큰이 계속 검증을 통과한다.
            FallbackJwkSource source = new FallbackJwkSource(returning(List.of()), fallbackKey, WINDOW);

            assertThat(source.get(anyKey, null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("조회 장애")
    class OutageTest {

        @Test
        void 원격_조회가_실패하면_폴백을_쓴다() {
            // auth-service가 내려간 상태에서 콜드 스타트한 서비스가 전면 검증 실패하는 것을 막는다.
            FallbackJwkSource source = new FallbackJwkSource(failing(), fallbackKey, WINDOW);

            assertThat(source.get(anyKey, null))
                    .extracting(JWK::getKeyID)
                    .containsExactly("fallback-kid");
        }

        @Test
        void 창_안에서는_계속_폴백을_쓴다() {
            MovableClock clock = new MovableClock();
            FallbackJwkSource source = source(failing(), clock);
            source.get(anyKey, null);

            clock.advance(WINDOW.minusMinutes(1));

            assertThat(source.get(anyKey, null))
                    .extracting(JWK::getKeyID)
                    .containsExactly("fallback-kid");
        }

        @Test
        void 창을_넘기면_폴백을_멈춘다() {
            // 기한이 없으면 폴백은 키 폐기를 무기한 우회하는 길이 된다.
            // 요청을 거절하는 편이 폐기된 키를 받아주는 것보다 낫다.
            MovableClock clock = new MovableClock();
            FallbackJwkSource source = source(failing(), clock);
            source.get(anyKey, null);

            clock.advance(WINDOW.plusMinutes(1));

            assertThat(source.get(anyKey, null)).isEmpty();
        }

        @Test
        void 원격이_한번이라도_회복되면_창이_초기화된다() {
            // 장애가 끊겼다 이어지는 동안 누적 시간으로 폴백이 막히면 안 된다.
            MovableClock clock = new MovableClock();
            AtomicReference<Boolean> healthy = new AtomicReference<>(false);
            JWKSource<SecurityContext> flaky = (selector, context) -> {
                if (healthy.get()) {
                    return List.of();
                }
                throw new IllegalStateException("원격 JWKS 연결 실패");
            };
            FallbackJwkSource source = source(flaky, clock);

            source.get(anyKey, null);
            clock.advance(WINDOW.minusMinutes(1));
            healthy.set(true);
            source.get(anyKey, null);          // 회복 — 창 초기화
            healthy.set(false);
            clock.advance(WINDOW.minusMinutes(1));

            assertThat(source.get(anyKey, null))
                    .extracting(JWK::getKeyID)
                    .containsExactly("fallback-kid");
        }

        @Test
        void 폴백은_공개키만_노출한다() {
            FallbackJwkSource source = new FallbackJwkSource(failing(), fallbackKey, WINDOW);

            assertThat(source.get(anyKey, null)).allMatch(jwk -> !jwk.isPrivate());
        }
    }
}
