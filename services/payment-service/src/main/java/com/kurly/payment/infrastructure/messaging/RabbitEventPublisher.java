package com.kurly.payment.infrastructure.messaging;

import com.kurly.payment.application.port.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * RabbitMQ 이벤트 발행.
 *
 * <p>라우팅 키는 이벤트 타입에서 만든다({@code PAYMENT_CANCELED} → {@code payment.canceled}).
 * 소비자는 {@code payment.*} 패턴으로 바인딩해 필요한 것만 받는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitEventPublisher implements EventPublisher {

    /** 소비자가 중복 처리를 걸러낼 때 보는 헤더. 본문을 열어보지 않고도 판단할 수 있게 한다. */
    static final String EVENT_ID_HEADER = "x-event-id";
    static final String EVENT_TYPE_HEADER = "x-event-type";

    private final RabbitTemplate rabbitTemplate;
    private final PaymentMessagingProperties properties;

    @Override
    public void publish(String eventId, String eventType, String payload) {
        String routingKey = toRoutingKey(eventType);
        rabbitTemplate.convertAndSend(properties.exchange(), routingKey, payload, message -> {
            // 브로커가 재시작해도 메시지가 남아야 한다. 결제 이벤트는 유실되면 주문 상태가 어긋난다.
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            message.getMessageProperties().setHeader(EVENT_ID_HEADER, eventId);
            message.getMessageProperties().setHeader(EVENT_TYPE_HEADER, eventType);
            return message;
        });
        log.info("결제 이벤트 발행: eventType={}, routingKey={}, eventId={}", eventType, routingKey, eventId);
    }

    private static String toRoutingKey(String eventType) {
        return eventType.toLowerCase(Locale.ROOT).replace('_', '.');
    }
}
