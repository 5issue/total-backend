package com.kurly.order.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.modulith.events.core.EventSerializer;

@Configuration(proxyBeanMethods = false)
// Spring Boot JPA 스캔 메커니즘에 Modulith 엔티티 패키지를 직접 보완 등록
@AutoConfigurationPackage(basePackages = "org.springframework.modulith.events.jpa")
public class ModulithEventSerializerConfig {

    @Bean
    @Primary
    public EventSerializer eventSerializer() {
        ObjectMapper jackson2Mapper = new ObjectMapper();
        jackson2Mapper.registerModule(new JavaTimeModule());

        return new EventSerializer() {
            @Override
            public Object serialize(Object event) {
                try {
                    return jackson2Mapper.writeValueAsString(event);
                } catch (Exception e) {
                    throw new RuntimeException("Modulith 이벤트 직렬화 실패", e);
                }
            }

            @Override
            public <T> T deserialize(Object serialized, Class<T> type) {
                try {
                    return jackson2Mapper.readValue(serialized.toString(), type);
                } catch (Exception e) {
                    throw new RuntimeException("Modulith 이벤트 역직렬화 실패", e);
                }
            }
        };
    }
}