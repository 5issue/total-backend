package com.kurly.oms.domain.order;

import com.kurly.oms.domain.common.StorageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OmsOrderItemUnitExceptionTest {

    @Nested
    @DisplayName("OmsOrderItem 생성 필드 유효성 예외 테스트")
    class CreateValidationTest {

        static Stream<Arguments> invalidItemParameters() {
            return Stream.of(
                    Arguments.of("orderItemId 필수 누락", (Runnable) () ->
                                    OmsOrderItem.create(null, 1001L, 2001L, StorageType.ROOM, 1, 1000L),
                            "원 주문 상품 ID는 필수입니다."),
                    Arguments.of("productId 필수 누락", (Runnable) () ->
                                    OmsOrderItem.create(101L, null, 2001L, StorageType.ROOM, 1, 1000L),
                            "상품 ID는 필수입니다."),
                    Arguments.of("skuId 필수 누락", (Runnable) () ->
                                    OmsOrderItem.create(101L, 1001L, null, StorageType.ROOM, 1, 1000L),
                            "SKU ID는 필수입니다."),
                    Arguments.of("storageType 필수 누락", (Runnable) () ->
                                    OmsOrderItem.create(101L, 1001L, 2001L, null, 1, 1000L),
                            "보관 온도대는 필수입니다."),
                    Arguments.of("quantity 0 이하 예외", (Runnable) () ->
                                    OmsOrderItem.create(101L, 1001L, 2001L, StorageType.ROOM, 0, 1000L),
                            "수량은 1개 이상이어야 합니다."),
                    Arguments.of("quantity 필수 누락", (Runnable) () ->
                                    OmsOrderItem.create(101L, 1001L, 2001L, StorageType.ROOM, null, 1000L),
                            "수량은 1개 이상이어야 합니다."),
                    Arguments.of("unitPrice 0 이하 예외", (Runnable) () ->
                                    OmsOrderItem.create(101L, 1001L, 2001L, StorageType.ROOM, 1, 0L),
                            "단가는 필수이며 0보다 커야 합니다."),
                    Arguments.of("unitPrice 필수 누락", (Runnable) () ->
                                    OmsOrderItem.create(101L, 1001L, 2001L, StorageType.ROOM, 1, null),
                            "단가는 필수이며 0보다 커야 합니다.")
            );
        }

        @ParameterizedTest(name = "예외 케이스: {0}")
        @MethodSource("invalidItemParameters")
        void 필드_유효성_미충족_시_예외가_발생한다(String caseName, Runnable action, String expectedMessage) {
            assertThatThrownBy(action::run)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(expectedMessage);
        }
    }
}