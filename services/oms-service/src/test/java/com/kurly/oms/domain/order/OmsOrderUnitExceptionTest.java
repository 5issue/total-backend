package com.kurly.oms.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class OmsOrderUnitExceptionTest {

    @Nested
    @DisplayName("OmsOrder 생성 필수값 유효성 예외 테스트")
    class CreateValidationTest {

        static Stream<Arguments> invalidOrderParameters() {
            OmsOrderItem mockItem = mock(OmsOrderItem.class);

            return Stream.of(
                    Arguments.of("orderId 필수 누락", (Runnable) () ->
                                    OmsOrder.create(null, "O01", "evt1", 1L, 1000L, "홍길동", "010", "06234", "서울", "101", List.of(mockItem)),
                            "주문 서비스 orderId는 필수입니다."),
                    Arguments.of("orderNo 빈값 예외", (Runnable) () ->
                                    OmsOrder.create(1L, "  ", "evt1", 1L, 1000L, "홍길동", "010", "06234", "서울", "101", List.of(mockItem)),
                            "주문번호는 필수입니다."),
                    Arguments.of("sourceEventId 빈값 예외", (Runnable) () ->
                                    OmsOrder.create(1L, "O01", "", 1L, 1000L, "홍길동", "010", "06234", "서울", "101", List.of(mockItem)),
                            "주문 이벤트 ID는 필수입니다."),
                    Arguments.of("regionId 필수 누락", (Runnable) () ->
                                    OmsOrder.create(1L, "O01", "evt1", null, 1000L, "홍길동", "010", "06234", "서울", "101", List.of(mockItem)),
                            "주문 서비스 regionId는 필수입니다."),
                    Arguments.of("paidAmount 0 이하 예외", (Runnable) () ->
                                    OmsOrder.create(1L, "O01", "evt1", 1L, 0L, "홍길동", "010", "06234", "서울", "101", List.of(mockItem)),
                            "결제 총 금액은 필수이며 0보다 커야 합니다."),
                    Arguments.of("paidAmount 필수 누락", (Runnable) () ->
                                    OmsOrder.create(1L, "O01", "evt1", 1L, null, "홍길동", "010", "06234", "서울", "101", List.of(mockItem)),
                            "결제 총 금액은 필수이며 0보다 커야 합니다."),
                    Arguments.of("recipientName 필수 누락", (Runnable) () ->
                                    OmsOrder.create(1L, "O01", "evt1", 1L, 1000L, "", "010", "06234", "서울", "101", List.of(mockItem)),
                            "수령인명은 필수입니다."),
                    Arguments.of("recipientPhone 필수 누락", (Runnable) () ->
                                    OmsOrder.create(1L, "O01", "evt1", 1L, 1000L, "홍길동", "", "06234", "서울", "101", List.of(mockItem)),
                            "수령인 연락처는 필수입니다."),
                    Arguments.of("postalCode 필수 누락", (Runnable) () ->
                                    OmsOrder.create(1L, "O01", "evt1", 1L, 1000L, "홍길동", "010", "", "서울", "101", List.of(mockItem)),
                            "우편번호는 필수입니다."),
                    Arguments.of("roadAddress 필수 누락", (Runnable) () ->
                                    OmsOrder.create(1L, "O01", "evt1", 1L, 1000L, "홍길동", "010", "06234", "", "", List.of(mockItem)),
                            "도로명 주소는 필수입니다."),
                    Arguments.of("items 빈 리스트 예외", (Runnable) () ->
                                    OmsOrder.create(1L, "O01", "evt1", 1L, 1000L, "홍길동", "010", "06234", "서울", "101", Collections.emptyList()),
                            "주문 품목은 최소 1개 이상이어야 합니다.")
            );
        }

        @ParameterizedTest(name = "예외 케이스: {0}")
        @MethodSource("invalidOrderParameters")
        void 필수값_유효성_미충족_시_예외가_발생한다(String caseName, Runnable action, String expectedMessage) {
            assertThatThrownBy(action::run)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(expectedMessage);
        }
    }

}