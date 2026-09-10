package com.kurly.payment.application.port;

import java.util.Optional;

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
     *
     * @param cancelId 취소 이력({@code payment_cancels})의 식별자. <b>PG 멱등키의 근거가 된다.</b>
     *                 호출마다 새 키를 만들면, PG는 처리했는데 응답이 유실돼 재시도할 때 PG가
     *                 같은 취소로 알아보지 못한다. 같은 취소 이력에는 항상 같은 키가 가야 한다
     */
    Cancellation cancel(String paymentKey, long amount, String reason, Long cancelId);

    /**
     * 주문번호로 결제 상태를 조회한다.
     *
     * <p>승인 요청이 타임아웃돼 <b>실제로 승인됐는지 알 수 없을 때</b> 진실을 확인하는 유일한 수단이다.
     * 우리 기록만으로는 판단할 수 없으므로 PG에 직접 묻는다.
     *
     * @return 해당 주문의 결제가 없으면 비어 있음
     */
    Optional<Inquiry> findByOrderId(Long orderId);

    /**
     * @param method     PG가 확정한 결제 수단. 우리가 아는 값으로 좁히지 않는다
     * @param receiptUrl 영수증 주소. 승인 시점에만 받을 수 있어 저장해 둔다
     */
    record Approval(String paymentKey, String method, String receiptUrl) {
    }

    /**
     * 조회 결과.
     *
     * <p>{@code approved}와 {@code pending}을 따로 둔다. "승인 안 됨"은 두 가지 뜻이 될 수 있는데,
     * <b>확정된 실패</b>와 <b>아직 진행 중</b>을 같게 다루면 결제될 수 있었던 건을 실패로 못박는다.
     *
     * @param approved 승인이 완료됐다. PG의 {@code DONE}
     * @param pending  아직 결론이 나지 않았다. 인증만 끝났거나 입금을 기다리는 중이다
     */
    record Inquiry(String paymentKey, String status, String method, String receiptUrl,
                   boolean approved, boolean pending) {
    }

    record Cancellation(String pgCancelKey) {
    }
}
