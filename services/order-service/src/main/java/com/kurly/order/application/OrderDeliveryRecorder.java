package com.kurly.order.application;

import com.kurly.common.exception.BusinessException;
import com.kurly.order.domain.common.OrderErrorCode;
import com.kurly.order.domain.order.DeliveryStatus;
import com.kurly.order.domain.order.Order;
import com.kurly.order.domain.order.OrderRepository;
import com.kurly.order.domain.order.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 배송 완료 상태 전이만 담당한다. 외부 연동은 하지 않는다.
 *
 * <p><b>{@link OrderDeliveryService}와 분리한 이유</b> — 외부 호출을 트랜잭션 안에서 하면
 * AI 서버가 느릴 때 DB 커넥션을 그만큼 붙잡는다. 상태 전이를 먼저 커밋하고 그 뒤에 호출해야
 * 한다. 같은 클래스의 메서드를 호출하면 프록시를 거치지 않아 트랜잭션 경계가 생기지 않으므로
 * 빈을 나눈다.
 */
@Component
@RequiredArgsConstructor
public class OrderDeliveryRecorder {

    private final OrderRepository orderRepository;

    /**
     * 주문을 배송 완료로 전이시키고, 냉장고에 넣을 품목을 함께 돌려준다.
     *
     * <p>행을 잠그고 읽는다. 관리자가 두 번 눌렀을 때 두 요청이 모두 "아직 DELIVERED 아님"을
     * 보고 통과하면 외부 연동이 두 번 일어난다.
     */
    @Transactional
    public DeliveredOrder markDelivered(Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORD_NOT_FOUND_ORDER));

        if (order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(OrderErrorCode.ORD_INVALID_STATUS, order.getStatus().name());
        }
        if (order.getDeliveryStatus() == DeliveryStatus.DELIVERED) {
            // 이중 적재를 막는 가장 바깥 방어선이다. AI 호출은 멱등하지 않다.
            throw new BusinessException(OrderErrorCode.ORD_CONFLICT_ALREADY_PROCESSED,
                    DeliveryStatus.DELIVERED.name());
        }

        order.markDelivered(LocalDateTime.now());

        return new DeliveredOrder(
                order.getId(),
                order.getMemberId(),
                order.getDeliveredAt(),
                order.getItems().stream()
                        .map(item -> new FridgeClient.FridgeItem(item.getProductId(), item.getQuantity()))
                        .toList());
    }

    /** 커밋된 배송 완료 사실. 트랜잭션 밖에서 쓰므로 엔티티가 아니라 값으로 넘긴다. */
    public record DeliveredOrder(
            Long orderId,
            Long memberId,
            LocalDateTime deliveredAt,
            List<FridgeClient.FridgeItem> items) {
    }
}
