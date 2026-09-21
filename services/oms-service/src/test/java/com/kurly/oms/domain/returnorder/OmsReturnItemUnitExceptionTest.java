package com.kurly.oms.domain.returnorder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OmsReturnItemUnitExceptionTest {

    @Nested
    @DisplayName("OmsReturnItem 생성 예외 테스트")
    class CreateValidationTest {

        @Test
        void OmsOrderItem이_null이면_예외가_발생한다() {
            assertThatThrownBy(() -> OmsReturnItem.create(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("OmsOrderItem은 필수입니다.");
        }
    }

}