package com.kurly.payment.infrastructure.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RabbitEventPublisherUnitTest {

    private static final String EXCHANGE = "payment.topic.exchange";

    @Mock RabbitTemplate rabbitTemplate;

    RabbitEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new RabbitEventPublisher(rabbitTemplate, new PaymentMessagingProperties(EXCHANGE));
    }

    /** 발행 시 넘긴 후처리기를 실제로 적용해 메시지 속성을 확인한다. */
    private MessageProperties capturedProperties() {
        ArgumentCaptor<MessagePostProcessor> captor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), any(String.class), any(Object.class), captor.capture());
        Message message = new Message(new byte[0], new MessageProperties());
        return captor.getValue().postProcessMessage(message).getMessageProperties();
    }

    @Nested
    @DisplayName("라우팅")
    class RoutingTest {

        @Test
        void 이벤트_타입에서_라우팅_키를_만든다() {
            // 소비자는 payment.* 패턴으로 바인딩해 필요한 것만 받는다.
            publisher.publish("event-1", "PAYMENT_CANCELED", "{}");

            verify(rabbitTemplate).convertAndSend(
                    eq(EXCHANGE), eq("payment.canceled"), eq((Object) "{}"), any(MessagePostProcessor.class));
        }

        @Test
        void 설정한_익스체인지로_보낸다() {
            publisher.publish("event-1", "PAYMENT_CANCELED", "{}");

            verify(rabbitTemplate).convertAndSend(
                    eq(EXCHANGE), any(String.class), any(Object.class), any(MessagePostProcessor.class));
        }
    }

    @Nested
    @DisplayName("메시지 속성")
    class MessagePropertiesTest {

        @Test
        void 브로커가_재시작해도_남도록_영속으로_보낸다() {
            // 결제 이벤트가 유실되면 주문 상태가 결제 상태와 어긋난 채 남는다.
            publisher.publish("event-1", "PAYMENT_CANCELED", "{}");

            assertThat(capturedProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        }

        @Test
        void 이벤트_ID와_타입을_헤더로_싣는다() {
            // 소비자가 본문을 열어보지 않고도 중복 여부를 판단할 수 있게 한다.
            publisher.publish("event-1", "PAYMENT_CANCELED", "{}");

            MessageProperties properties = capturedProperties();
            // getHeader는 제네릭이라 대상 타입을 명시하지 않으면 assertThat 오버로드가 모호해진다.
            String eventId = properties.getHeader(RabbitEventPublisher.EVENT_ID_HEADER);
            String eventType = properties.getHeader(RabbitEventPublisher.EVENT_TYPE_HEADER);
            assertThat(eventId).isEqualTo("event-1");
            assertThat(eventType).isEqualTo("PAYMENT_CANCELED");
        }
    }

    @Nested
    @DisplayName("설정 기본값")
    class PropertiesTest {

        @Test
        void 익스체인지를_지정하지_않으면_기본값을_쓴다() {
            assertThat(new PaymentMessagingProperties(null).exchange()).isEqualTo("payment.topic.exchange");
            assertThat(new PaymentMessagingProperties("  ").exchange()).isEqualTo("payment.topic.exchange");
        }
    }
}
