package com.kurly.payment.infrastructure.order;

import com.kurly.payment.application.port.OrderClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 로컬 전용 주문 서비스 스텁.
 *
 * <p>order-service의 내부 API({@code GET /internal/v1/orders/{orderId}},
 * {@code POST /internal/v1/orders/{orderId}/complete-pay})가 만들어진 뒤 실제 어댑터로 교체한다.
 *
 * <p><b>{@code payment.order.client=stub}일 때만 등록된다.</b> 기본값은 실제 어댑터라 설정을
 * 빠뜨려도 스텁이 끼어들지 않는다. 운영에서 스텁이 주문 검증을 대신하면 금액 위변조 검증이
 * 무력화되므로, 켜는 쪽을 명시적으로 만들었다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payment.order.client", havingValue = "stub")
public class StubOrderClient implements OrderClient {

    /** 로컬 확인용 고정 주문. 이 금액과 다르게 요청하면 금액 불일치 경로를 볼 수 있다. */
    static final long STUB_AMOUNT = 32_000L;
    static final Long STUB_OWNER_USER_ID = 1L;

    @Override
    public OrderSnapshot fetch(Long orderId) {
        log.warn("주문 스텁을 사용합니다. 실제 주문 금액·소유자를 조회하지 않습니다: orderId={}", orderId);
        return new OrderSnapshot(orderId, STUB_OWNER_USER_ID, STUB_AMOUNT, true);
    }

    @Override
    public void completePayment(Long orderId) {
        log.warn("주문 스텁으로 결제 완료 통보를 흉내 냅니다: orderId={}", orderId);
    }
}
