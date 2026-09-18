package com.kurly.auth.infrastructure.oauth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 보안팀이 확인해 준 검증 규칙을 테스트로 고정한다(검토요청서 A-8).
 * 규칙을 완화하면 여기가 먼저 깨지도록 음성 케이스를 촘촘히 둔다.
 */
class ReturnToPathUnitTest {

    @Nested
    @DisplayName("허용 — 내부 상대 경로")
    class AllowedTest {

        @ParameterizedTest
        @ValueSource(strings = {
                "/",
                "/checkout",
                "/products/123",
                "/products/123?tab=review",
                "/search?q=%EC%82%AC%EA%B3%BC",
                "/a+b",
        })
        void 내부_상대경로는_통과한다(String path) {
            assertThat(ReturnToPath.sanitize(path)).isPresent();
        }

        @Test
        void 퍼센트_인코딩을_푼_값을_돌려준다() {
            // 되돌려줄 때 딱 한 번만 인코딩되도록 디코딩된 형태를 보관한다.
            assertThat(ReturnToPath.sanitize("/search?q=%EC%82%AC%EA%B3%BC"))
                    .contains("/search?q=사과");
        }

        @Test
        void 플러스는_공백으로_바뀌지_않는다() {
            // URLDecoder였다면 "/a b"가 된다. 경로에서 +는 리터럴이다.
            assertThat(ReturnToPath.sanitize("/a+b")).contains("/a+b");
        }
    }

    @Nested
    @DisplayName("거부 — 외부로 나갈 수 있는 값")
    class RejectedTest {

        @ParameterizedTest
        @ValueSource(strings = {
                "https://evil.com",
                "http://evil.com/path",
                "//evil.com",              // 스킴 상대 URL — 브라우저가 외부로 해석한다
                "///evil.com",
                "/\\evil.com",             // 역슬래시 우회
                "/\\/evil.com",
                "\\\\evil.com",
                "javascript:alert(1)",
                "data:text/html,<script>alert(1)</script>",
                "checkout",                // 슬래시로 시작하지 않음
                "",
        })
        void 외부로_나갈_수_있는_값은_버린다(String path) {
            assertThat(ReturnToPath.sanitize(path)).isEmpty();
        }

        @Test
        void 디코딩_후에_외부가_되는_값도_버린다() {
            // 원문만 보면 "/"로 시작해 통과할 것 같지만, 풀면 "//evil.com"이다.
            assertThat(ReturnToPath.sanitize("%2F%2Fevil.com")).isEmpty();
        }

        @Test
        void 제어문자가_섞이면_버린다() {
            assertThat(ReturnToPath.sanitize("/checkout\n/evil")).isEmpty();
            assertThat(ReturnToPath.sanitize("/checkout" + (char) 0x00)).isEmpty();  // NUL
            assertThat(ReturnToPath.sanitize("/checkout" + (char) 0x7F)).isEmpty();  // DEL
        }

        @Test
        void 상위_경로_이동은_버린다() {
            assertThat(ReturnToPath.sanitize("/../admin")).isEmpty();
            assertThat(ReturnToPath.sanitize("/a/../../b")).isEmpty();
        }

        @Test
        void 해시는_받지_않는다() {
            // 프론트가 해시를 쓰지 않기로 확정했다. 받지 않으면 인코딩을 신경 쓸 일이 없다.
            assertThat(ReturnToPath.sanitize("/products/123#reviews")).isEmpty();
        }

        @Test
        void 길이_제한을_넘으면_버린다() {
            assertThat(ReturnToPath.sanitize("/" + "a".repeat(512))).isEmpty();
        }

        @Test
        void 깨진_퍼센트_시퀀스는_버린다() {
            assertThat(ReturnToPath.sanitize("/checkout%")).isEmpty();
            assertThat(ReturnToPath.sanitize("/checkout%ZZ")).isEmpty();
        }

        @Test
        void 값이_없으면_비어_있다() {
            assertThat(ReturnToPath.sanitize(null)).isEmpty();
            assertThat(ReturnToPath.sanitize("   ")).isEmpty();
        }
    }
}
