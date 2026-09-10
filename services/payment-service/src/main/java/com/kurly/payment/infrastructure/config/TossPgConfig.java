package com.kurly.payment.infrastructure.config;

import com.kurly.payment.infrastructure.pg.TossPaymentProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 토스 연동 설정을 실제 연동을 쓸 때만 등록한다.
 *
 * <p>{@link TossPaymentProperties}는 시크릿 키가 없으면 기동을 중단시킨다. 스텁을 쓰는 로컬에서는
 * 키가 없는 것이 정상이므로, 설정 자체를 조건부로 두지 않으면 로컬이 기동하지 못한다.
 */
@Configuration
@ConditionalOnProperty(name = "payment.pg.client", havingValue = "toss", matchIfMissing = true)
@EnableConfigurationProperties(TossPaymentProperties.class)
public class TossPgConfig {
}
