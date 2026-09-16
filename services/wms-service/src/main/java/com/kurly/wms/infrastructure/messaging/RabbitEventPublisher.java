package com.kurly.wms.infrastructure.messaging;

import com.kurly.wms.application.port.EventPublisher;
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

/** RabbitMQ로 아웃박스 이벤트를 발행한다. */
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
            Confirm confirm = correlationData.getFuture().get(5, TimeUnit.SECONDS);

            if (!confirm.ack()) {
                throw new RuntimeException("RabbitMQ Broker NACK 수신: reason=" + confirm.reason());
            }
            if (correlationData.getReturned() != null) {
                throw new RuntimeException("RabbitMQ 라우팅 실패 (No Queue Bound): routingKey=" + routingKey);
            }

            log.info("아웃박스 이벤트 발행 성공 (ACK 수신): exchange={}, routingKey={}, typeId={}", exchange, routingKey, typeId);
        } catch (Exception e) {
            log.error("아웃박스 이벤트 발행 실패 (스케줄러가 재시도하도록 예외 전파): {}", e.getMessage());
            throw new RuntimeException("RabbitMQ publish failed", e);
        }
    }
}
