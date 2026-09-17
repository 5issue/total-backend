package com.kurly.wms.application.port;

public interface EventPublisher {

    /**
     * @param exchange   발행 대상 익스체인지
     * @param routingKey 라우팅 키
     * @param typeId     소비자가 역직렬화할 타입의 완전 정규화 이름({@code __TypeId__} 헤더 값)
     * @param payload    JSON 문자열
     */
    void publish(String exchange, String routingKey, String typeId, String payload);
}
