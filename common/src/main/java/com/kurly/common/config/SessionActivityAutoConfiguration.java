package com.kurly.common.config;

import com.kurly.common.security.activity.RabbitSessionActivityRecorder;
import com.kurly.common.security.activity.SessionActivityChannels;
import com.kurly.common.security.activity.SessionActivityRecorder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

/**
 * 세션 활동 이벤트 발행 자동설정(인증인가_설계서 1.6).
 *
 * <p>공통 인증 처리기가 켜진 서비스에서만 의미가 있으므로 {@code kurly.security.enabled}를
 * 함께 조건으로 둔다. 끄고 싶으면 {@code kurly.security.activity.enabled=false}.
 *
 * <p><b>큐와 바인딩은 여기서 선언하지 않는다.</b> 소비자인 auth-service가 선언한다.
 * 발행자마다 선언하면 인자가 어긋났을 때 {@code PRECONDITION_FAILED}로 기동이 깨진다.
 */
@AutoConfiguration(after = RabbitMqAutoConfiguration.class)
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnProperty(prefix = "kurly.security", name = "enabled", havingValue = "true")
public class SessionActivityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "sessionActivityExchange")
    public TopicExchange sessionActivityExchange() {
        return new TopicExchange(SessionActivityChannels.EXCHANGE, true, false);
    }

    @Bean
    @ConditionalOnMissingBean(SessionActivityRecorder.class)
    @ConditionalOnProperty(prefix = "kurly.security.activity", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public SessionActivityRecorder sessionActivityRecorder(
            RabbitTemplate rabbitTemplate,
            @Value("${spring.application.name:unknown}") String serviceName,
            @Value("${kurly.security.activity.debounce:60s}") Duration debounce) {
        return new RabbitSessionActivityRecorder(rabbitTemplate, serviceName, debounce);
    }
}
