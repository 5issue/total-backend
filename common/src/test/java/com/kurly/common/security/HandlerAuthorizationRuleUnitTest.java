package com.kurly.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HandlerAuthorizationRuleUnitTest {

    @Nested
    @DisplayName("역할 허용 판정")
    class PermitTest {

        @Test
        void 지정한_역할만_통과시킨다() {
            HandlerAuthorizationRule rule = HandlerAuthorizationRule.forRoles(Role.ADMIN);

            assertThat(rule.permits(Role.ADMIN)).isTrue();
            assertThat(rule.permits(Role.USER)).isFalse();
        }

        @Test
        void 인증만_요구하는_규칙은_모든_역할을_통과시킨다() {
            HandlerAuthorizationRule rule = HandlerAuthorizationRule.forAuthenticated();

            assertThat(rule.permits(Role.USER)).isTrue();
            assertThat(rule.permits(Role.ADMIN)).isTrue();
            assertThat(rule.publicAccess()).isFalse();
        }

        @Test
        void 공개_규칙은_인증을_요구하지_않는다() {
            assertThat(HandlerAuthorizationRule.forPublicApi().publicAccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("빈 역할 배열 거부")
    class EmptyRolesTest {

        @Test
        void 역할을_지정하지_않으면_규칙을_만들_수_없다() {
            // 허용하면 빈 집합이 "역할을 가리지 않음"으로 해석되어
            // @RequireRole이 조용히 @Authenticated로 격하된다.
            assertThatThrownBy(HandlerAuthorizationRule::forRoles)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("역할을 하나 이상");
        }

        @Test
        void null_배열도_거부한다() {
            assertThatThrownBy(() -> HandlerAuthorizationRule.forRoles((Role[]) null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
