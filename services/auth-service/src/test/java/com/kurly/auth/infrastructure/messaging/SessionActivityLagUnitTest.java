package com.kurly.auth.infrastructure.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("활동 반영 지연 관측")
class SessionActivityLagUnitTest {

    private static final Duration STALE_AFTER = Duration.ofSeconds(60);

    @Test
    void 적체가_없으면_지연을_주장하지_않는다() {
        // 반영 시각이 오래됐어도 큐가 비어 있으면 "한가해서"일 뿐이다.
        SessionActivityLag lag = new SessionActivityLag(STALE_AFTER);
        lag.onFlush(Instant.now().minusSeconds(600), 0L);

        assertThat(lag.current()).isZero();
    }

    @Test
    void 적체가_있으면_반영_시각부터의_지연을_돌려준다() {
        SessionActivityLag lag = new SessionActivityLag(STALE_AFTER);
        lag.onFlush(Instant.now().minusSeconds(300), 42L);

        assertThat(lag.current()).isBetween(Duration.ofSeconds(295), Duration.ofSeconds(310));
    }

    @Test
    void 관측이_낡으면_지연_보정을_중단한다() {
        // flush가 완료되지 못하면 snapshot이 갱신되지 않는다. 그 값을 계속 믿으면
        // 지연이 무한히 커져 유휴 판정이 영구히 우회된다 — 통제가 조용히 꺼진 상태다.
        Instant observedAt = Instant.parse("2026-09-24T00:00:00Z");
        MutableClock clock = new MutableClock(observedAt);
        SessionActivityLag lag = new SessionActivityLag(STALE_AFTER, clock);
        lag.onFlush(observedAt.minusSeconds(900), 42L);

        clock.advance(STALE_AFTER.minusSeconds(1));
        assertThat(lag.current()).isPositive();          // 아직 신선하다 — 보정 유지

        clock.advance(Duration.ofSeconds(2));
        assertThat(lag.current()).isZero();              // 낡았다 — 엄격 판정으로 복귀
    }

    /** 흐르는 시각을 제어한다. */
    static class MutableClock extends java.time.Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public java.time.Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    @Test
    void 초기_상태에서는_지연이_없다() {
        assertThat(new SessionActivityLag(STALE_AFTER).current()).isZero();
    }
}
