package com.kurly.product.infrastructure.messaging;

import com.kurly.product.application.port.EventPublisher;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.connection.CorrelationData.Confirm;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ로 아웃박스 이벤트를 발행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitEventPublisher implements EventPublisher {

    private static final String TYPE_ID_HEADER = "__TypeId__";

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publish(String exchange, String routingKey, String typeId, String payload) {
        CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());

        rabbitTemplate.convertAndSend(exchange, routingKey, payload, message -> {
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            message.getMessageProperties().setContentType(MessageProperties.CONTENT_TYPE_JSON);
            message.getMessageProperties().setHeader(TYPE_ID_HEADER, typeId);
            return message;
        }, correlationData);
        try {
            // 3. ✋ 핵심! 브로커의 ACK/NACK 응답을 최대 5초간 대기합니다.
            Confirm confirm = correlationData.getFuture().get(5, TimeUnit.SECONDS);

            if (!confirm.ack()) {
                // NACK을 받은 경우 (브로커 수락 실패) -> 예외를 던져 Outbox 상태가 PUBLISHED로 안 바뀌게 함!
                throw new RuntimeException("RabbitMQ Broker NACK 수신: reason=" + confirm.reason());
            }

            // 4. Mandatory Return 발생 여부 확인 (라우팅 키 불일치 등으로 큐에 안 들어간 경우)
            if (correlationData.getReturned() != null) {
                throw new RuntimeException("RabbitMQ 라우팅 실패 (No Queue Bound): routingKey=" + routingKey);
            }

            log.info("아웃박스 이벤트 발행 성공 (ACK 수신): exchange={}, routingKey={}, typeId={}", exchange, routingKey, typeId);

        } catch (Exception e) {
            log.error("아웃박스 이벤트 발행 실패 (스케줄러가 재시도하도록 예외 전파): {}", e.getMessage());
            // 예외를 밖으로 던져야 tryPublish()에서 Outbox 상태를 PUBLISHED로 바꾸지 않고 PENDING으로 남겨둡니다.
            throw new RuntimeException("RabbitMQ publish failed", e);
        }
    }
}
