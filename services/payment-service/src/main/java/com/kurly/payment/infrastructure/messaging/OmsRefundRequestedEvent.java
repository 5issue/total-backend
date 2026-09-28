package com.kurly.payment.infrastructure.messaging;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * OMS가 반품 환불을 요청하는 이벤트(payment 추가 통신 명세).
 *
 * <p>OMS가 환불 금액을 책정한 뒤 <b>전체환불요청을 1회</b> 발행한다.
 * <ul>
 *   <li>반품 아이템이 냉장·냉동뿐이면: 관리자 승인 시 발행(WMS 검수 없음)</li>
 *   <li>상온이 섞여 있으면: 관리자 승인 → WMS 검수 결과 수신 시 발행</li>
 * </ul>
 *
 * <p><b>필드 구성은 OMS의 {@code OmsRefundRequestedEvent}를 그대로 따른다.</b> 한쪽만 바꾸면
 * 역직렬화가 조용히 {@code null}을 채우므로, 변경은 양쪽 합의가 필요하다.
 *
 * @param omsReturnId 반품 건 식별자. <b>OMS가 아직 싣지 않는다</b> — 명세의 응답
 *                    {@code PaymentRefundCompletedMessage.omsReturnId}가 OMS의 조회 키인데
 *                    요청 이벤트에 그 값이 없어 되돌려줄 수 없다. OMS 추가 후 동작한다
 *                    (없으면 처리하지 않고 DLQ로 보낸다)
 * @param refundAmount 환불할 금액. OMS의 {@code totalRefundAmount}와 같아야 하며 응답에 그대로 싣는다
 * @param deductedFee  차감한 배송비. {@code refundAmount}에 이미 반영된 값이 아니라 별도 기록이다
 */
public record OmsRefundRequestedEvent(
        UUID eventId,
        Long omsOrderId,
        Long omsReturnId,
        Long orderId,
        Long refundAmount,
        Long deductedFee,
        List<Long> omsOrderItemIds,
        LocalDateTime occurredAt
) {
}
