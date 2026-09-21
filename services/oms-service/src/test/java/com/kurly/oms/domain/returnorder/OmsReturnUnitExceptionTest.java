package com.kurly.oms.domain.returnorder;

import com.kurly.oms.domain.order.OmsOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class OmsReturnUnitExceptionTest {

    @Nested
    @DisplayName("OmsReturn 생성 예외 테스트")
    class CreateValidationTest {

        static Stream<Arguments> invalidReturnParameters() {
            return Stream.of(
                    Arguments.of("omsOrder 필수 누락", (Runnable) () -> OmsReturn.createFromOrder(null), "omsOrder는 필수입니다."),
                    Arguments.of("orderOmsItems 빈 리스트 예외", (Runnable) () -> OmsReturn.createFromOrder(mock(OmsOrder.class)), "주문 품목이 비어있습니다.")
            );
        }

        @ParameterizedTest(name = "예외 케이스: {0}")
        @MethodSource("invalidReturnParameters")
        void 필수값_유효성_미충족_시_예외가_발생한다(String caseName, Runnable action, String expectedMessage) {
            assertThatThrownBy(action::run)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(expectedMessage);
        }
    }
}