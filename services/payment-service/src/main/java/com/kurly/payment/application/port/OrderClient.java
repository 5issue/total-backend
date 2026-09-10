package com.kurly.payment.application.port;

/**
 * 주문 서비스 연동 포트(주문-결제 시퀀스 단계 2·3).
 *
 * <p>결제 금액과 소유자는 <b>주문 서비스가 진실의 원천</b>이다. 클라이언트가 보낸 금액을 그대로
 * 믿으면 위변조를 막을 수 없으므로, 승인 전에 주문을 직접 조회해 대조한다.
 *
 * <p><b>구현체는 아직 없다.</b> order-service의 내부 API가 만들어진 뒤 infrastructure에 붙인다.
 */
public interface OrderClient {

    /**
     * 주문 스냅샷 조회.
     *
     * @throws com.kurly.payment.exception.OrderNotFoundException 주문이 없는 경우
     */
    OrderSnapshot fetch(Long orderId);

    /**
     * 결제 완료를 주문 서비스에 통보한다. 주문이 이미 만료됐으면 거절된다.
     *
     * @throws OrderAlreadyExpiredException 결제 유효시간(5분)이 지나 주문이 만료된 경우.
     *                                      호출부는 보상 취소를 수행해야 한다.
     */
    void completePayment(Long orderId);

    /**
     * @param ownerUserId 주문 소유자. 결제 요청자와 일치해야 한다
     * @param totalAmount 주문 총액(원 단위 정수). 요청 금액과 일치해야 한다
     * @param payable     결제를 받을 수 있는 상태인지. 유효시간 판단은 주문 서비스가 한다
     */
    record OrderSnapshot(Long orderId, Long ownerUserId, long totalAmount, boolean payable) {
    }

    /**
     * 결제는 승인됐는데 주문이 이미 만료된 상태.
     * 업무 예외가 아니라 <b>보상 트랜잭션을 유발하는 신호</b>라 별도 타입으로 둔다.
     */
    class OrderAlreadyExpiredException extends RuntimeException {

        public OrderAlreadyExpiredException(Long orderId) {
            super("주문이 이미 만료되었습니다: orderId=" + orderId);
        }
    }
}
