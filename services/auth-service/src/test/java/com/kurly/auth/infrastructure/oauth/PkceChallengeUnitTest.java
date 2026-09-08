package com.kurly.auth.infrastructure.oauth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class PkceChallengeUnitTest {

    @Nested
    @DisplayName("code_verifier 생성")
    class VerifierTest {

        @Test
        void RFC_7636이_정한_43자_이상_128자_이하다() {
            String verifier = PkceChallenge.generate().verifier();

            assertThat(verifier.length()).isBetween(43, 128);
        }

        @Test
        void 매번_다른_값이_생성된다() {
            assertThat(PkceChallenge.generate().verifier())
                    .isNotEqualTo(PkceChallenge.generate().verifier());
        }

        @Test
        void URL에_그대로_실을_수_있는_문자만_쓴다() {
            String verifier = PkceChallenge.generate().verifier();

            // base64url 문자셋(A-Za-z0-9-_)만 허용된다. 패딩(=)이 있으면 URL 인코딩이 필요해진다.
            assertThat(verifier).matches("[A-Za-z0-9\\-_]+");
        }
    }

    @Nested
    @DisplayName("code_challenge 파생")
    class ChallengeTest {

        @Test
        void verifier의_SHA256을_base64url로_인코딩한_값이다() throws Exception {
            PkceChallenge pkce = PkceChallenge.generate();

            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(pkce.verifier().getBytes(StandardCharsets.US_ASCII));
            String expected = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);

            assertThat(pkce.challenge()).isEqualTo(expected);
        }

        @Test
        void challenge에서_verifier를_역산할_수_없다() {
            PkceChallenge pkce = PkceChallenge.generate();

            // 해시이므로 값이 달라야 한다. 같다면 verifier가 그대로 노출되는 것이다.
            assertThat(pkce.challenge()).isNotEqualTo(pkce.verifier());
        }

        @Test
        void 메서드는_S256이다() {
            assertThat(PkceChallenge.CHALLENGE_METHOD).isEqualTo("S256");
        }
    }

    @Nested
    @DisplayName("state 생성")
    class StateTest {

        @Test
        void 매번_다른_값이_생성된다() {
            assertThat(PkceChallenge.generateState()).isNotEqualTo(PkceChallenge.generateState());
        }
    }
}
