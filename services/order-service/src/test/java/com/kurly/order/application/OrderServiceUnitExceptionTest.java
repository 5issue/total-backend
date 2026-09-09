package com.kurly.order.application;

import com.kurly.common.exception.BusinessException;
import com.kurly.order.domain.claim.OrderClaimRepository;
import com.kurly.order.domain.common.OrderErrorCode;
import com.kurly.order.domain.order.OrderRepository;
import com.kurly.order.presentation.dto.ReturnRequestDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceUnitExceptionTest {

    @Mock
    OrderRepository orderRepository;
    @Mock
    OrderClaimRepository orderClaimRepository;
    @Mock
    ApplicationEventPublisher eventPublisher;
    @Mock
    OrderExternalService externalService;
    @InjectMocks
    OrderService orderService;

    @Nested
    @DisplayName("주문 조회 예외 테스트")
    class GetOrderTest {

        @Test
        void 존재하지_않는_주문은_도메인_에러를_반환한다() {
            when(orderRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getForPayment(404L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OrderErrorCode.ORD_NOT_FOUND_ORDER);
        }
    }

    @Nested
    @DisplayName("반품 증빙 예외 테스트")
    class ReturnEvidenceTest {

        @Test
        void 증빙이_필요한_사유는_사진_없이_접수할_수_없다() {
            ReturnRequestDto request = new ReturnRequestDto("RTN02", "상품 불량", List.of());

            assertThatThrownBy(() -> orderService.requestReturn(1L, 1L, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OrderErrorCode.ORD_MISSING_RETURN_EVIDENCE);
        }
    }
}
