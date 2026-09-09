package com.kurly.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class JwtVerificationPropertiesUnitTest {

    private static JwtVerificationProperties of(boolean enabled, String issuer, String audience) {
        return new JwtVerificationProperties(enabled, issuer, audience, null, null, null, null);
    }

    @Nested
    @DisplayName("기본값")
    class DefaultTest {

        @Test
        void clockSkew와_감사_패키지에_기본값이_채워진다() {
            JwtVerificationProperties properties = of(false, null, null);

            assertThat(properties.clockSkew()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.auditPackages()).containsExactly("com.kurly");
        }

        @Test
        void 비활성이면_issuer와_audience를_요구하지_않는다() {
            // 처리기를 쓰지 않는 서비스가 common 의존만으로 기동에 실패하면 안 된다.
            assertThatCode(() -> of(false, null, null)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("활성 시 필수값 검증")
    class RequiredWhenEnabledTest {

        @Test
        void issuer가_없으면_기동을_막는다() {
            assertThatThrownBy(() -> of(true, null, "kurly-api"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("kurly.security.issuer");
        }

        @Test
        void audience가_비면_기동을_막는다() {
            // 비면 대상 검증이 사실상 꺼져 다른 audience용 토큰이 통과한다.
            assertThatThrownBy(() -> of(true, "https://auth.kurly.local", "  "))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("kurly.security.audience");
        }

        @Test
        void 주입되지_않은_플레이스홀더는_값으로_보지_않는다() {
            // Boot의 Binder는 미해석 플레이스홀더를 예외로 만들지 않고 리터럴로 남긴다.
            // 문자열이 비어 있지 않아 빈값 검사만으로는 걸러지지 않는다.
            assertThatThrownBy(() -> of(true, "${JWT_ISSUER}", "kurly-api"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("환경변수가 주입되지 않았습니다");
        }

        @Test
        void 값이_모두_있으면_통과한다() {
            assertThatCode(() -> of(true, "https://auth.kurly.local", "kurly-api"))
                    .doesNotThrowAnyException();
        }
    }
}
