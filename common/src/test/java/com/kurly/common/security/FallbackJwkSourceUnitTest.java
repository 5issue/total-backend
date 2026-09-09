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

import java.util.List;

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
            FallbackJwkSource source = new FallbackJwkSource(returning(List.of(remoteKey)), fallbackKey);

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
            FallbackJwkSource source = new FallbackJwkSource(returning(List.of()), fallbackKey);

            assertThat(source.get(anyKey, null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("조회 장애")
    class OutageTest {

        @Test
        void 원격_조회가_실패하면_폴백을_쓴다() {
            // auth-service가 내려간 상태에서 콜드 스타트한 서비스가 전면 검증 실패하는 것을 막는다.
            FallbackJwkSource source = new FallbackJwkSource(failing(), fallbackKey);

            assertThat(source.get(anyKey, null))
                    .extracting(JWK::getKeyID)
                    .containsExactly("fallback-kid");
        }

        @Test
        void 폴백은_공개키만_노출한다() {
            FallbackJwkSource source = new FallbackJwkSource(failing(), fallbackKey);

            assertThat(source.get(anyKey, null)).allMatch(jwk -> !jwk.isPrivate());
        }
    }
}
