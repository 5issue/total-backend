package com.kurly.order.application;

import com.kurly.order.domain.order.DeliveryStatus;
import com.kurly.order.presentation.dto.DeliveryCompleteResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 배송 완료 처리와 그에 따른 외부 연동.
 *
 * <p><b>트랜잭션을 걸지 않는다.</b> 상태 전이는 {@link OrderDeliveryRecorder}가 자신의 트랜잭션에서
 * 끝내고, 외부 호출은 그것이 커밋된 뒤에 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderDeliveryService {

    private final OrderDeliveryRecorder recorder;
    private final FridgeClient fridgeClient;

    /**
     * 주문을 배송 완료로 바꾸고 품목을 사용자의 냉장고에 넣는다.
     *
     * <p><b>냉장고 적재가 실패해도 배송 완료는 되돌리지 않는다.</b> 배송 완료는 주문 도메인의
     * 사실이고 냉장고는 부가 기능이다. 부가 기능 때문에 되돌리면 반품 자격까지 함께 막힌다
     * (반품은 배송 완료 주문만 가능하다). 또 되돌린 뒤 관리자가 다시 누르면, AI가 이미 받았는데
     * 응답만 끊긴 경우에 수량이 두 배가 된다.
     *
     * <p>대신 실패를 로그와 응답({@code fridgeSynced})에 남긴다. 누락은 사람이 보정해야 한다 —
     * 자동 재시도는 비멱등 누적 때문에 금지돼 있다(AI팀 합의). 멱등 키가 생기면 재시도를 켠다.
     */
    public DeliveryCompleteResponseDto completeDelivery(Long orderId) {
        OrderDeliveryRecorder.DeliveredOrder delivered = recorder.markDelivered(orderId);

        boolean fridgeSynced = true;
        try {
            fridgeClient.addItems(delivered.memberId(), delivered.items());
        } catch (Exception e) {
            fridgeSynced = false;
            log.error("냉장고 적재 실패. 배송 완료는 유지한다: orderId={}, memberId={}, 품목={}건",
                    delivered.orderId(), delivered.memberId(), delivered.items().size(), e);
        }

        return new DeliveryCompleteResponseDto(
                delivered.orderId(),
                DeliveryStatus.DELIVERED.name(),
                delivered.deliveredAt(),
                fridgeSynced);
    }
}
