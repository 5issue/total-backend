package com.kurly.auth.infrastructure.messaging;

import com.kurly.common.security.activity.SessionActivityChannels;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 세션 활동 이벤트 소비 설정(세션활동_이벤트_통신명세 2장).
 *
 * <p><b>큐는 소비자인 여기서만 선언한다.</b> 발행자(전 서비스)가 각자 선언하면 인자가 한 곳이라도
 * 어긋났을 때 {@code PRECONDITION_FAILED}로 기동이 깨진다.
 */
@Configuration
@EnableScheduling
public class SessionActivityMessagingConfig {

    @Bean
    public SessionActivityLag sessionActivityLag() {
        return new SessionActivityLag();
    }

    @Bean
    public Queue sessionActivityDlq() {
        return QueueBuilder.durable(SessionActivityChannels.DLQ).build();
    }

    /**
     * <b>인자 세 개는 모두 의도적이다.</b>
     *
     * <ul>
     *   <li>{@code drop-head} — 소비자가 최신 시각만 취해 병합하므로 오래된 이벤트는 버려도
     *       정보 손실이 없다. 기본 동작(무제한 적재 → 브로커 워터마크 → <b>발행자 블로킹</b>)은
     *       전 서비스의 인증 요청을 멈추게 하므로 반드시 피한다.</li>
     *   <li>{@code ttl} — 유휴 한도보다 훨씬 짧다. 적체가 구조적으로 이 시간어치를 넘지 못한다.</li>
     *   <li>{@code maxLength} — TTL이 못 막는 순간적 폭주의 상한.</li>
     * </ul>
     *
     * <p>⚠️ 이미 만들어진 큐의 인자는 나중에 바꿀 수 없다. 바꾸려면 큐를 지우고 다시 만들어야 한다.
     */
    @Bean
    public Queue sessionActivityQueue() {
        return QueueBuilder.durable(SessionActivityChannels.QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(SessionActivityChannels.DLQ)
                .maxLength(SessionActivityChannels.MAX_LENGTH)
                .overflow(QueueBuilder.Overflow.dropHead)
                .ttl(SessionActivityChannels.MESSAGE_TTL_MS)
                .build();
    }

    @Bean
    public Binding sessionActivityBinding(Queue sessionActivityQueue, TopicExchange sessionActivityExchange) {
        return BindingBuilder.bind(sessionActivityQueue)
                .to(sessionActivityExchange)
                .with(SessionActivityChannels.ROUTING_KEY);
    }
}
