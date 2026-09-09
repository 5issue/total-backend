package com.kurly.auth.application;

import com.kurly.auth.domain.entity.AuthAdmin;
import com.kurly.auth.domain.repository.AuthAdminRepository;
import com.kurly.auth.infrastructure.persistence.AuthAdminJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 잠금 카운터는 JPQL 벌크 갱신으로 처리하므로 실제 DB 없이는 검증되지 않는다.
 *
 * <p>실행: {@code AUTH_INTEGRATION_TEST=true ./gradlew :auth-service:test}
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "AUTH_INTEGRATION_TEST", matches = "true")
class AdminLoginAttemptServiceIntegrationTest {

    @Autowired AdminLoginAttemptService adminLoginAttemptService;
    @Autowired AuthAdminRepository authAdminRepository;
    @Autowired AuthAdminJpaRepository authAdminJpaRepository;

    @AfterEach
    void cleanUp() {
        authAdminJpaRepository.deleteAll();
    }

    private AuthAdmin createAdmin() {
        return authAdminRepository.save(AuthAdmin.builder()
                .loginId("ops-" + System.nanoTime())
                .password("$2a$10$notarealhash")
                .adminId(System.nanoTime() % 1_000_000)
                .build());
    }

    /** 조회도 도메인 인터페이스로 호출한다. JpaRepository 타입으로 부르면 모호성 오류가 난다. */
    private AuthAdmin reload(Long id) {
        return authAdminRepository.findById(id).orElseThrow();
    }

    @Nested
    @DisplayName("연속 실패 잠금")
    class LockTest {

        @Test
        void 임계치에_도달하면_잠기고_실패_횟수는_0으로_돌아간다() {
            AuthAdmin admin = createAdmin();

            for (int i = 0; i < AdminLoginAttemptService.MAX_RETRY_COUNT; i++) {
                adminLoginAttemptService.recordFailure(admin.getId());
            }

            AuthAdmin locked = reload(admin.getId());
            assertThat(locked.getLockedUntil()).isNotNull();
            // 카운터를 남겨두면 잠금이 풀린 뒤 1회 실패로 곧바로 재잠금된다.
            assertThat(locked.getRetryCount()).isZero();
        }

        @Test
        void 잠금_이후_한_번_실패해도_다시_잠기지_않는다() {
            AuthAdmin admin = createAdmin();
            for (int i = 0; i < AdminLoginAttemptService.MAX_RETRY_COUNT; i++) {
                adminLoginAttemptService.recordFailure(admin.getId());
            }
            LocalDateTime lockedAt = reload(admin.getId()).getLockedUntil();

            adminLoginAttemptService.recordFailure(admin.getId());

            AuthAdmin after = reload(admin.getId());
            assertThat(after.getRetryCount()).isEqualTo(1);
            // 잠금 시각이 그대로여야 한다. 갱신됐다면 1회 실패로 재잠금된 것이다.
            assertThat(after.getLockedUntil()).isEqualTo(lockedAt);
        }

        @Test
        void 로그인에_성공하면_잠금과_실패_횟수가_해제된다() {
            AuthAdmin admin = createAdmin();
            adminLoginAttemptService.recordFailure(admin.getId());

            adminLoginAttemptService.recordSuccess(admin.getId());

            AuthAdmin after = reload(admin.getId());
            assertThat(after.getRetryCount()).isZero();
            assertThat(after.getLockedUntil()).isNull();
        }
    }
}
