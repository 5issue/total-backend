package com.kurly.auth.application;

import com.kurly.auth.domain.repository.AdminRefreshTokenRepository;
import com.kurly.auth.domain.repository.UserRefreshTokenRepository;
import com.kurly.common.security.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("활동 이벤트 반영")
class SessionActivityServiceUnitTest {

    @Mock UserRefreshTokenRepository userRefreshTokenRepository;
    @Mock AdminRefreshTokenRepository adminRefreshTokenRepository;

    @InjectMocks SessionActivityService sessionActivityService;

    @Test
    void 회원_활동은_회원_세션에만_반영한다() {
        given(userRefreshTokenRepository.touchActiveSessions(any(), any(), any())).willReturn(1);

        sessionActivityService.touch(Role.USER, 1001L, Instant.now());

        verify(userRefreshTokenRepository).touchActiveSessions(eq(1001L), any(), any());
        verify(adminRefreshTokenRepository, never()).touchActiveSessions(any(), any(), any());
    }

    @Test
    void 관리자_활동은_관리자_세션에만_반영한다() {
        given(adminRefreshTokenRepository.touchActiveSessions(any(), any(), any())).willReturn(1);

        sessionActivityService.touch(Role.ADMIN, 9999L, Instant.now());

        verify(adminRefreshTokenRepository).touchActiveSessions(eq(9999L), any(), any());
        verify(userRefreshTokenRepository, never()).touchActiveSessions(any(), any(), any());
    }

    @Test
    void 반영할_세션이_없어도_예외를_던지지_않는다() {
        // 이미 더 최근 기록이 있거나 폐기·만료된 세션이다. 정상 상황이다.
        given(userRefreshTokenRepository.touchActiveSessions(any(), any(), any())).willReturn(0);

        sessionActivityService.touch(Role.USER, 1001L, Instant.now());

        verify(userRefreshTokenRepository).touchActiveSessions(eq(1001L), any(), any());
    }

    @Test
    void 시스템_기본_시간대로_변환해_저장한다() {
        // created_at·expires_at이 모두 시스템 기본 시간대 기준이라, 여기만 UTC로 저장하면
        // 같은 컬럼에 두 시계가 섞여 시차만큼 유휴로 오판한다.
        Instant occurredAt = Instant.parse("2026-09-24T01:23:45Z");
        given(userRefreshTokenRepository.touchActiveSessions(any(), any(), any())).willReturn(1);

        sessionActivityService.touch(Role.USER, 1001L, occurredAt);

        ArgumentCaptor<LocalDateTime> usedAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(userRefreshTokenRepository).touchActiveSessions(eq(1001L), usedAt.capture(), any());
        assertThat(usedAt.getValue())
                .isEqualTo(LocalDateTime.ofInstant(occurredAt, ZoneId.systemDefault()));
    }
}
