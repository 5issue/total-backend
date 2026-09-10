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
        void 클러스터_내부_주소는_평문을_허용한다() {
            // 운영은 ALB에서 TLS를 종료하고 내부 구간은 재암호화하지 않는 것이 확정 사항이다.
            // 여기서 https를 강제하면 전 서비스가 기동하지 못한다.
            assertThatCode(() -> configuration.jwkSource(withJwksUri(
                    "http://auth-service.kurly.svc.cluster.local/.well-known/jwks.json")))
                    .doesNotThrowAnyException();
        }

        @Test
        void 클러스터_내부처럼_보이는_외부_호스트는_거부한다() {
            // svc.cluster.local을 도메인 일부로 흉내 낸 외부 주소는 접미사가 다르다.
            assertThatThrownBy(() -> configuration.jwkSource(
                    withJwksUri("http://svc.cluster.local.attacker.example/.well-known/jwks.json")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("https여야 합니다");
        }

        @Test
        void 짧은_클러스터_이름은_FQDN을_쓰도록_거부한다() {
            // auth-service.kurly 형태도 클러스터 안에서는 해석되지만, 검색 도메인에 의존해
            // 환경에 따라 다른 대상을 가리킬 수 있다. FQDN만 허용한다.
            assertThatThrownBy(() -> configuration.jwkSource(
                    withJwksUri("http://auth-service.kurly/.well-known/jwks.json")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("클러스터 내부 주소");
        }

        @Test
        void jwks_uri가_비면_기동을_막는다() {
            assertThatThrownBy(() -> configuration.jwkSource(withJwksUri("  ")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("kurly.security.jwks-uri가 필요합니다");
        }
    }
}
