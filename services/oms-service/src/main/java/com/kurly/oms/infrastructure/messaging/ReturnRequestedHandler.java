package com.kurly.oms.infrastructure.messaging;

import com.kurly.oms.application.OmsReturnService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReturnRequestedHandler {

    private final OmsReturnService returnService;

    @RabbitListener(queues = OmsRabbitMqConfig.RETURN_REQUESTED_QUEUE)
    public void handle(Map<String, Object> event) {
        try {
            returnService.receive(new OmsReturnService.ReturnRequestedCommand(
                    required(event, "eventId"),
                    requiredLong(event, "returnId"),
                    requiredLong(event, "orderId"),
                    requiredLong(event, "userId"),
                    requiredLong(event, "paymentId"),
                    required(event, "reason"),
                    optional(event, "reasonDetail"),
                    requiredLong(event, "expectedRefundAmount")
            ));
        } catch (IllegalArgumentException e) {
            log.error("처리할 수 없는 반품 접수 이벤트를 DLQ로 보냅니다: event={}", event, e);
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
    }

    private static String required(Map<String, Object> event, String key) {
        String value = optional(event, key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("반품 접수 이벤트에 " + key + "가 없습니다.");
        }
        return value;
    }

    private static Long requiredLong(Map<String, Object> event, String key) {
        return Long.valueOf(required(event, key));
    }

    private static String optional(Map<String, Object> event, String key) {
        Object value = event.get(key);
        return value == null ? null : value.toString();
    }

}
