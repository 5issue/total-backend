package com.kurly.common.security.activity;

/**
 * 세션 활동 이벤트의 브로커 채널 이름. 발행자(전 서비스)와 소비자(auth-service)가 공유한다.
 *
 * <p><b>명명 규약 예외</b>: 기존 컨벤션은 {@code {발행서비스}.topic.exchange}이지만
 * (예: {@code order.topic.exchange}) 이 이벤트는 발행자가 전 서비스라 소유 서비스가 없다.
 * 그래서 익스체인지는 도메인 이름을 쓰고, 큐는 컨벤션대로 소비자 기준으로 짓는다.
 *
 * @see <a href="file:../../../../../../../../docs/세션활동_이벤트_통신명세.md">세션활동 이벤트 통신명세</a>
 */
public final class SessionActivityChannels {

    public static final String EXCHANGE = "session.topic.exchange";
    public static final String ROUTING_KEY = "session.user.activity";
    public static final String QUEUE = "auth.user-activity.queue";
    public static final String DLQ = "auth.user-activity.dlq";

    /** 큐 상한. 넘치면 가장 오래된 것부터 버린다. */
    public static final int MAX_LENGTH = 100_000;

    /**
     * 메시지 수명(ms). 유휴 한도(15~30분)보다 훨씬 짧게 둔다.
     * 이보다 오래된 활동 기록은 지연 보정이 무시할 값이라 쓸모가 없고,
     * 적체가 구조적으로 이 시간어치를 넘지 못하게 하는 안전장치다.
     */
    public static final int MESSAGE_TTL_MS = 120_000;

    private SessionActivityChannels() {
    }
}
