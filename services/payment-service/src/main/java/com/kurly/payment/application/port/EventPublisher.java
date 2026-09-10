package com.kurly.payment.application.port;

/**
 * 도메인 이벤트 발행 포트.
 *
 * <p>아웃박스에 적재된 이벤트를 브로커로 내보낸다. 업무 로직이 브로커 구현을 알지 않도록 분리한다.
 */
public interface EventPublisher {

    /**
     * @param eventId   소비자의 중복 처리 방어 기준. 메시지 헤더로도 실어 보낸다
     * @param eventType 예: {@code PAYMENT_CANCELED}
     * @param payload   JSON 문자열. JWT 원문은 담기지 않는다
     */
    void publish(String eventId, String eventType, String payload);
}
