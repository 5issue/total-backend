package com.kurly.payment.support;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 결제 통합 테스트 공통 설정.
 *
 * <p>여기서 확인하는 것은 단위 테스트가 <b>구조적으로 닿을 수 없는</b> 것들이다. 네이티브 쿼리
 * ({@code FOR UPDATE SKIP LOCKED}, {@code DELETE ... ORDER BY ... LIMIT}), 생성 컬럼과 유니크 제약,
 * 그리고 트랜잭션 경계다. 목으로 대신하면 SQL이 틀려도 초록불이 켜진다.
 *
 * <p><b>테스트에 {@code @Transactional}을 걸지 않는다.</b> 서비스가 스스로 커밋하는 동작을 봐야
 * 하는데, 테스트가 트랜잭션을 감싸면 커밋 경계가 사라져 확인하려던 것이 사라진다.
 *
 * <p><b>백그라운드 워커를 끈다.</b> 켜 두면 워커가 테스트가 만든 결제·아웃박스를 집어가 검증
 * 대상이 시험 도중 바뀐다.
 *
 * <p>로컬 MySQL(docker compose up -d payment-mysql)이 필요하므로 기본 빌드에서는 건너뛴다.
 * 실행: {@code PAYMENT_INTEGRATION_TEST=true ./gradlew :payment-service:test}
 * 팀에서 Testcontainers를 도입하면 이 조건은 제거할 수 있다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = "payment.scheduling.enabled=false")
@EnabledIfEnvironmentVariable(named = "PAYMENT_INTEGRATION_TEST", matches = "true")
public @interface PaymentIntegrationTest {
}
