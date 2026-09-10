package com.kurly.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnresolvedPlaceholderGuardUnitTest {

    /** Boot가 설정 파일에서 온 프로퍼티 소스에 붙이는 이름 형식. 가드는 이 소스만 검사한다. */
    private static final String CONFIG_SOURCE =
            "Config resource 'class path resource [application-prod.yml]' via location 'optional:classpath:/'";

    private final UnresolvedPlaceholderGuard guard = new UnresolvedPlaceholderGuard();

    /**
     * <b>주변 환경을 끊어낸다.</b> {@code StandardEnvironment}는 시스템 환경변수를 프로퍼티 소스로
     * 갖고 있어, 실행 환경에 {@code DB_HOST} 같은 변수가 있으면 미주입을 재현하려던 플레이스홀더가
     * 해석돼 버린다. CI에서 간헐적으로 깨지는 테스트가 되므로 두 소스를 제거한다.
     */
    private static StandardEnvironment environment(String profile, String sourceName, Map<String, Object> values) {
        StandardEnvironment environment = hermetic();
        environment.setActiveProfiles(profile);
        environment.getPropertySources().addFirst(new MapPropertySource(sourceName, new LinkedHashMap<>(values)));
        return environment;
    }

    private static StandardEnvironment hermetic() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources()
                .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources()
                .remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        return environment;
    }

    private void run(StandardEnvironment environment) {
        guard.postProcessEnvironment(environment, new SpringApplication());
    }

    @Nested
    @DisplayName("미주입 검출")
    class DetectionTest {

        @Test
        void 주입되지_않은_환경변수가_있으면_기동을_막는다() {
            StandardEnvironment environment = environment("prod", CONFIG_SOURCE,
                    Map.of("spring.rabbitmq.host", "${RABBITMQ_HOST}"));

            assertThatThrownBy(() -> run(environment))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("주입되지 않은 환경변수")
                    .hasMessageContaining("spring.rabbitmq.host");
        }

        @Test
        void 발견한_것을_모두_모아_한번에_알린다() {
            // 하나씩 고쳐가며 재기동하지 않도록 목록을 한 번에 보여준다.
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("spring.datasource.url", "jdbc:mysql://${DB_HOST}:${DB_PORT}/db");
            values.put("spring.rabbitmq.host", "${RABBITMQ_HOST}");
            values.put("order-service.base-url", "${ORDER_SERVICE_BASE_URL}");

            assertThatThrownBy(() -> run(environment("prod", CONFIG_SOURCE, values)))
                    .hasMessageContaining("spring.datasource.url")
                    .hasMessageContaining("spring.rabbitmq.host")
                    .hasMessageContaining("order-service.base-url");
        }

        @Test
        void 값이_일부만_미주입이어도_잡는다() {
            // URL처럼 여러 플레이스홀더가 섞인 값은 하나만 빠져도 못 쓴다.
            StandardEnvironment environment = environment("prod", CONFIG_SOURCE, Map.of(
                    "db.host", "mysql.internal",
                    "spring.datasource.url", "jdbc:mysql://${db.host}:${DB_PORT}/db"));

            assertThatThrownBy(() -> run(environment))
                    .hasMessageContaining("spring.datasource.url");
        }

        @Test
        void 모두_주입됐으면_통과한다() {
            StandardEnvironment environment = environment("prod", CONFIG_SOURCE, Map.of(
                    "db.host", "mysql.internal",
                    "spring.datasource.url", "jdbc:mysql://${db.host}:3306/db"));

            assertThatCode(() -> run(environment)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("검사 범위")
    class ScopeTest {

        @Test
        void 로컬_프로파일은_검사하지_않는다() {
            // 개발 중 기동을 막을 이유가 없다. 로컬 설정은 모든 값에 기본값을 둔다.
            StandardEnvironment environment = environment("local", CONFIG_SOURCE,
                    Map.of("spring.rabbitmq.host", "${RABBITMQ_HOST}"));

            assertThatCode(() -> run(environment)).doesNotThrowAnyException();
        }

        @Test
        void 테스트_프로파일도_검사하지_않는다() {
            StandardEnvironment environment = environment("test", CONFIG_SOURCE,
                    Map.of("spring.rabbitmq.host", "${RABBITMQ_HOST}"));

            assertThatCode(() -> run(environment)).doesNotThrowAnyException();
        }

        @Test
        void 프로파일을_지정하지_않으면_검사하지_않는다() {
            StandardEnvironment environment = hermetic();
            environment.getPropertySources().addFirst(new MapPropertySource(
                    CONFIG_SOURCE, new LinkedHashMap<>(Map.of("spring.rabbitmq.host", "${RABBITMQ_HOST}"))));

            assertThatCode(() -> run(environment)).doesNotThrowAnyException();
        }

        @Test
        void 설정_파일이_아닌_소스는_검사하지_않는다() {
            // 환경 전체를 훑으면 logging.pattern.console처럼 ${}를 정상적으로 포함하는
            // Boot 기본값까지 걸려 오탐이 난다.
            StandardEnvironment environment = environment("prod", "systemProperties",
                    Map.of("logging.pattern.console", "%clr(${LOG_LEVEL_PATTERN})"));

            assertThatCode(() -> run(environment)).doesNotThrowAnyException();
        }
    }
}
