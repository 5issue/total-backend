package com.kurly.payment.application.port;

/**
 * PG(토스페이먼츠) 연동 포트.
 *
 * <p>인터페이스로 분리한 이유는 두 가지다. 승인·취소 로직을 실제 PG 없이 검증할 수 있어야 하고,
 * PG 교체나 다중 PG 지원이 생겼을 때 업무 로직이 흔들리지 않아야 한다.
 *
 * <p><b>구현체는 아직 없다.</b> 샌드박스 키와 연동 정책이 정해진 뒤 infrastructure에 붙인다.
 */
public interface PgClient {

    /**
     * 결제 승인.
     *
     * @throws com.kurly.payment.exception.PaymentDeclinedException 잔액 부족·한도 초과로 거절된 경우
     */
    Approval approve(String paymentKey, Long orderId, long amount);

    /**
     * 결제 취소. 부분 취소를 위해 금액을 함께 보낸다.
     * 실패는 예외로 알리며, 호출부가 취소 이력에 남기고 재시도 대상으로 넘긴다.
     */
    Cancellation cancel(String paymentKey, long amount, String reason);

    /**
     * @param method     PG가 확정한 결제 수단. 우리가 아는 값으로 좁히지 않는다
     * @param receiptUrl 영수증 주소. 승인 시점에만 받을 수 있어 저장해 둔다
     */
    record Approval(String paymentKey, String method, String receiptUrl) {
    }

    record Cancellation(String pgCancelKey) {
    }
}
