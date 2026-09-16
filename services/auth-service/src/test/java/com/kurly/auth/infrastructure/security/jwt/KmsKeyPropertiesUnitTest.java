package com.kurly.auth.infrastructure.security.jwt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KmsKeyPropertiesUnitTest {

    private static final String KEY_ID = "arn:aws:kms:ap-northeast-2:111122223333:key/abc";

    @Nested
    @DisplayName("서명키 설정")
    class KeyIdTest {

        @Test
        void 비어_있으면_기동을_막는다() {
            // 서명키를 못 찾으면 토큰을 아예 발급하지 못한다. 런타임 전에 잡는다.
            assertThatThrownBy(() -> new KmsKeyProperties(null, null, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("jwt.kms.key-id가 필요합니다");
        }

        @Test
        void 주입되지_않은_플레이스홀더는_값으로_보지_않는다() {
            // Boot의 Binder는 미해석 플레이스홀더를 리터럴로 남긴다. 값이 있는 것처럼 보인다.
            assertThatThrownBy(() -> new KmsKeyProperties("${JWT_KMS_KEY_ID}", null, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("환경변수가 주입되지 않았습니다");
        }

        @Test
        void 값이_있으면_통과한다() {
            assertThatCode(() -> new KmsKeyProperties(KEY_ID, null, null)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("회전용 이전 키")
    class PreviousKeyTest {

        @Test
        void 비워두면_회전_중이_아니다() {
            assertThat(new KmsKeyProperties(KEY_ID, null, null).hasPreviousKey()).isFalse();
            assertThat(new KmsKeyProperties(KEY_ID, "", null).hasPreviousKey()).isFalse();
        }

        @Test
        void 값을_주면_회전_중으로_본다() {
            assertThat(new KmsKeyProperties(KEY_ID, "arn:aws:kms:...:key/old", null).hasPreviousKey())
                    .isTrue();
        }

        @Test
        void 주입되지_않은_플레이스홀더는_기동을_막는다() {
            // 회전 중인데 구 키가 안 들어가면 구 키로 서명된 토큰이 전부 거부된다.
            // 조용히 "회전 아님"으로 넘어가면 그 사실을 알아채지 못한다.
            assertThatThrownBy(() -> new KmsKeyProperties(KEY_ID, "${JWT_KMS_PREVIOUS_KEY_ID}", null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("jwt.kms.previous-key-id");
        }
    }
}
