package com.kurly.wms.infrastructure.messaging;

import com.kurly.wms.application.port.EventPublisher;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
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

        // payload는 이미 JSON으로 직렬화된 문자열이다. convertAndSend(..., Object, ...)로 보내면
        // 설정된 MessageConverter(JacksonJsonMessageConverter)가 이 문자열을 "JSON으로 변환할
        // 객체"로 보고 다시 한 번 직렬화해(따옴표로 감싸고 이스케이프) 이중 인코딩된 바디가
        // 나간다 — 소비자는 바디를 파싱하면 문자열 스칼라 하나만 얻는다. 이미 만들어진 JSON
        // 텍스트는 그 바이트 그대로 보내야 한다.
        Message message = MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setHeader(TYPE_ID_HEADER, typeId)
                .build();

        rabbitTemplate.send(exchange, routingKey, message, correlationData);

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
