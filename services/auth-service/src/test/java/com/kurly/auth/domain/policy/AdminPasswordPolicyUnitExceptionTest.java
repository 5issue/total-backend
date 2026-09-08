package com.kurly.auth.domain.policy;

import com.kurly.common.exception.InvalidValueException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminPasswordPolicyUnitExceptionTest {

    @Nested
    @DisplayName("길이 규칙")
    class LengthTest {

        @Test
        void 빈_비밀번호는_거부된다() {
            assertThatThrownBy(() -> AdminPasswordPolicy.validate("  "))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        void 최소_길이_미만은_거부된다() {
            assertThatThrownBy(() -> AdminPasswordPolicy.validate("Ab1!short"))
                    .isInstanceOf(InvalidValueException.class)
                    .hasMessageContaining("12자 이상");
        }

        @Test
        void BCrypt가_잘라내는_72바이트_초과는_거부된다() {
            String tooLong = "Aa1!" + "x".repeat(69);

            assertThatThrownBy(() -> AdminPasswordPolicy.validate(tooLong))
                    .isInstanceOf(InvalidValueException.class)
                    .hasMessageContaining("72바이트");
        }

        @Test
        void 한글은_UTF8_바이트_기준으로_계산된다() {
            // 한글 1자 = 3바이트. 25자면 75바이트로 한도를 넘는다.
            String korean = "Aa1!" + "가".repeat(25);

            assertThatThrownBy(() -> AdminPasswordPolicy.validate(korean))
                    .isInstanceOf(InvalidValueException.class)
                    .hasMessageContaining("72바이트");
        }
    }

    @Nested
    @DisplayName("문자 조합 규칙")
    class CompositionTest {

        @Test
        void 숫자가_없으면_거부된다() {
            assertThatThrownBy(() -> AdminPasswordPolicy.validate("OnlyLetters!!"))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        void 특수문자가_없으면_거부된다() {
            assertThatThrownBy(() -> AdminPasswordPolicy.validate("OnlyLetters12"))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        void 영문이_없으면_거부된다() {
            assertThatThrownBy(() -> AdminPasswordPolicy.validate("1234567890!@"))
                    .isInstanceOf(InvalidValueException.class);
        }
    }
}
