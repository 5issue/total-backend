package com.kurly.auth.application;

import com.kurly.auth.application.dto.TokenPair;
import com.kurly.auth.domain.entity.AuthAdmin;
import com.kurly.auth.domain.repository.AuthAdminRepository;
import com.kurly.auth.exception.AccountLockedException;
import com.kurly.common.exception.ForbiddenException;
import com.kurly.common.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 백오피스 관리자 로그인. 소셜 전용인 고객과 달리 로그인 아이디 + 비밀번호로 인증한다.
 *
 * <p>이 클래스에는 {@code @Transactional}을 두지 않는다. BCrypt 검증(수십~수백 ms)을
 * 트랜잭션이 감싸면 그 시간만큼 커넥션을 점유해, 한 계정을 노린 공격이 커넥션 풀을 고갈시킨다.
 * 저장소 갱신은 {@link AdminLoginAttemptService}·{@link AuthTokenService}의 짧은 트랜잭션에서만 일어난다.
 */
@Slf4j
@Service
public class AdminAuthService {

    /**
     * 아이디 존재 여부가 드러나지 않도록 자격증명 실패를 모두 같은 메시지로 응답한다
     * (시큐어코딩가이드 BE-17 — 불필요한 정보 노출 금지).
     */
    public static final String INVALID_CREDENTIALS_MESSAGE = "아이디 또는 비밀번호가 일치하지 않습니다.";

    /** 명세가 정한 비활성 계정 응답 메시지(403). */
    public static final String DISABLED_ACCOUNT_MESSAGE = "비활성화된 관리자 계정입니다.";

    /** 잠금 안내 메시지(423). 잠금 시간과 어긋나지 않도록 정책값에서 만들어 쓴다. */
    public static final String ACCOUNT_LOCKED_MESSAGE =
            "연속적인 비밀번호 오류로 요청이 거부 되었습니다. %d분 후 다시 시도해주세요."
                    .formatted(AdminLoginAttemptService.LOCK_DURATION.toMinutes());

    private final AuthAdminRepository authAdminRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;
    private final AdminLoginAttemptService adminLoginAttemptService;

    /** 존재하지 않는 아이디일 때도 해시 검증에 드는 시간을 동일하게 맞추기 위한 더미 해시. */
    private final String dummyPasswordHash;

    public AdminAuthService(AuthAdminRepository authAdminRepository,
                            PasswordEncoder passwordEncoder,
                            AuthTokenService authTokenService,
                            AdminLoginAttemptService adminLoginAttemptService) {
        this.authAdminRepository = authAdminRepository;
        this.passwordEncoder = passwordEncoder;
        this.authTokenService = authTokenService;
        this.adminLoginAttemptService = adminLoginAttemptService;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public TokenPair login(String loginId, String rawPassword) {
        AuthAdmin admin = authAdminRepository.findByLoginId(loginId).orElse(null);

        if (admin == null) {
            // 해시 검증을 건너뛰면 응답 시간 차이로 아이디 존재 여부가 노출된다.
            passwordEncoder.matches(rawPassword, dummyPasswordHash);
            log.info("존재하지 않는 관리자 아이디로 로그인 시도");
            throw invalidCredentials();
        }

        // 잠긴 계정은 해시 검증 전에 차단한다. 잠금 이후의 반복 시도가 CPU를 소모하지 못하게 하려는 것으로,
        // 잠금 자체가 공격 수단이 되는 상황에서 비용을 공격자에게 돌려주지 않기 위함이다.
        if (admin.isLocked(LocalDateTime.now())) {
            log.info("잠긴 관리자 계정 로그인 시도: authAdminId={}", admin.getId());
            throw new AccountLockedException(ACCOUNT_LOCKED_MESSAGE);
        }

        if (!passwordEncoder.matches(rawPassword, admin.getPassword())) {
            log.info("관리자 비밀번호 불일치: authAdminId={}", admin.getId());
            adminLoginAttemptService.recordFailure(admin.getId());
            throw invalidCredentials();
        }

        // 비밀번호가 맞은 뒤에 상태를 확인한다. 순서를 뒤집으면 비밀번호를 모르는 사람에게도
        // 계정의 존재·상태가 드러난다.
        if (admin.isDisabled()) {
            log.info("비활성화된 관리자 로그인 시도: authAdminId={}", admin.getId());
            throw new ForbiddenException(DISABLED_ACCOUNT_MESSAGE);
        }
        adminLoginAttemptService.recordSuccess(admin.getId());
        return authTokenService.issueAdminTokens(admin);
    }

    private static UnauthorizedException invalidCredentials() {
        return new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
    }
}
