package com.kurly.auth.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class AdminPasswordPolicyUnitTest {

    @Nested
    @DisplayName("정책을 만족하는 비밀번호")
    class ValidPasswordTest {

        @Test
        void 영문_숫자_특수문자를_포함한_12자는_통과한다() {
            assertThatCode(() -> AdminPasswordPolicy.validate("Str0ng!Passw0rd"))
                    .doesNotThrowAnyException();
        }

        @Test
        void 정확히_72바이트는_통과한다() {
            String exactly72 = "Aa1!" + "x".repeat(68);

            assertThatCode(() -> AdminPasswordPolicy.validate(exactly72))
                    .doesNotThrowAnyException();
        }
    }
}
