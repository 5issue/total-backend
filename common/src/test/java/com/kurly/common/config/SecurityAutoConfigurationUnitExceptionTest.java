package com.kurly.common.config;

import com.kurly.common.security.JwtVerificationProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityAutoConfigurationUnitExceptionTest {

    private final SecurityAutoConfiguration configuration = new SecurityAutoConfiguration();

    private static JwtVerificationProperties withJwksUri(String jwksUri) {
        return new JwtVerificationProperties(
                true, "https://auth.kurly.local", "kurly-api", jwksUri, null, null, null);
    }

    @Nested
    @DisplayName("JWKS 전송 구간 검증")
    class TransportTest {

        @Test
        void 평문_원격_JWKS는_기동을_막는다() {
            // 중간자가 JWKS를 바꿔치기하면 자신의 개인키로 서명한 토큰이 검증을 통과한다.
            assertThatThrownBy(() -> configuration.jwkSource(
                    withJwksUri("http://auth.internal/.well-known/jwks.json")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("https여야 합니다");
        }

        @Test
        void https면_통과한다() {
            assertThatCode(() -> configuration.jwkSource(
                    withJwksUri("https://auth.internal/.well-known/jwks.json")))
                    .doesNotThrowAnyException();
        }

        @Test
        void 로컬_호스트는_평문을_허용한다() {
            // 로컬 개발은 http://localhost:8081을 쓴다. 프로파일이 아니라 호스트로 가른다.
            assertThatCode(() -> configuration.jwkSource(
                    withJwksUri("http://localhost:8081/.well-known/jwks.json")))
                    .doesNotThrowAnyException();
        }

        @Test
        void jwks_uri가_비면_기동을_막는다() {
            assertThatThrownBy(() -> configuration.jwkSource(withJwksUri("  ")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("kurly.security.jwks-uri가 필요합니다");
        }
    }
}
