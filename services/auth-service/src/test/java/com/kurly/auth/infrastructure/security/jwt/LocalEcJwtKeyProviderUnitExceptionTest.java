package com.kurly.auth.infrastructure.security.jwt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalEcJwtKeyProviderUnitExceptionTest {

    private static JwtProperties properties(String privateJwk) {
        return new JwtProperties("https://auth.kurly.local", "kurly-api",
                Duration.ofMinutes(30), Duration.ofDays(14), privateJwk);
    }

    private static MockEnvironment profile(String name) {
        return new MockEnvironment().withProperty("spring.profiles.active", name);
    }

    @Nested
    @DisplayName("서명키 누락")
    class MissingKeyTest {

        @Test
        void local에서는_임시_키를_생성한다() {
            assertThatCode(() -> new LocalEcJwtKeyProvider(properties(null), profile("local")))
                    .doesNotThrowAnyException();
        }

        @Test
        void local이_아니면_기동을_막는다() {
            // 인스턴스마다 다른 키로 서명하게 되어 JWKS를 받아간 서비스가 검증에 실패하고,
            // 재기동하면 발급한 토큰이 모두 무효가 된다.
            assertThatThrownBy(() -> new LocalEcJwtKeyProvider(properties(null), profile("prod")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("local 프로파일에서만 허용");
        }
    }

    @Nested
    @DisplayName("주입되지 않은 환경변수")
    class UnresolvedPlaceholderTest {

        @Test
        void 플레이스홀더_리터럴은_값으로_보지_않는다() {
            // Boot의 Binder는 미해석 플레이스홀더를 리터럴로 남기므로 hasText()가 true가 된다.
            // 걸러내지 않으면 "EC JWK로 해석하지 못했습니다"라는 엉뚱한 원인으로 실패한다.
            assertThatThrownBy(() ->
                    new LocalEcJwtKeyProvider(properties("${JWT_PRIVATE_JWK}"), profile("prod")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("환경변수가 주입되지 않았습니다");
        }

        @Test
        void local에서도_동일하게_거부한다() {
            assertThatThrownBy(() ->
                    new LocalEcJwtKeyProvider(properties("${JWT_PRIVATE_JWK}"), profile("local")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("환경변수가 주입되지 않았습니다");
        }
    }
}
