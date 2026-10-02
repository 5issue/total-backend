package com.kurly.order.application;

import java.util.List;

/**
 * AI 서비스의 냉장고 적재 연동 지점.
 *
 * <p>배송이 완료된 주문의 품목을 사용자의 냉장고에 넣는다. 기획상 "배송 완료 시 자동 추가"이므로
 * 결제 시점이 아니라 배송 완료 시점에만 호출한다.
 *
 * <p><b>이 호출은 멱등하지 않다.</b> AI 쪽이 수량을 누적하므로 같은 요청이 두 번 닿으면 두 배가
 * 된다. 그래서 자동 재시도를 하지 않으며(AI팀 요청), 중복 방지는 주문의 배송 완료 상태 전이가
 * 한 번만 허용되는 것으로 막는다.
 */
public interface FridgeClient {

    /**
     * 냉장고에 품목을 넣는다.
     *
     * @throws RuntimeException 호출에 실패하면 던진다. 호출부가 배송 완료 자체를 되돌릴지
     *                          판단한다.
     */
    void addItems(Long userId, List<FridgeItem> items);

    /**
     * @param productId 상품 식별자
     * @param quantity  수량
     */
    record FridgeItem(Long productId, int quantity) {
    }
}
