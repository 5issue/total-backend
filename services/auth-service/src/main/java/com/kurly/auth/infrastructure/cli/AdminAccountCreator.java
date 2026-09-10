package com.kurly.auth.infrastructure.cli;

import com.kurly.auth.domain.entity.AuthAdmin;
import com.kurly.auth.domain.policy.AdminPasswordPolicy;
import com.kurly.auth.domain.repository.AuthAdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 관리자 계정 생성 운영 CLI.
 * 관리자 회원가입 API를 두지 않기로 한 결정에 따라, 계정 생성은 이 경로로만 수행한다.
 *
 * <pre>{@code
 * java -jar auth-service.jar \
 *   --spring.profiles.active=admin-cli \
 *   --admin.login-id=ops-admin \
 *   --admin.admin-id=1001
 * }</pre>
 *
 * <p>비밀번호는 인자로 받지 않는다. 명령행 인자는 셸 히스토리와 {@code ps} 출력에 남아
 * 자격증명이 노출되기 때문이다(시큐어코딩가이드 BE-17). 실행 후 표준입력으로 입력받는다.
 */
@Slf4j
@Component
@Profile("admin-cli")
@RequiredArgsConstructor
public class AdminAccountCreator implements ApplicationRunner {

    private static final String LOGIN_ID_ARG = "admin.login-id";
    private static final String ADMIN_ID_ARG = "admin.admin-id";
    private static final int LOGIN_ID_MAX_LENGTH = 50;

    private final AuthAdminRepository authAdminRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        String loginId = requireLoginId(args);
        Long adminId = requireAdminId(args);
        String rawPassword = readPassword();

        AdminPasswordPolicy.validate(rawPassword);

        if (authAdminRepository.findByLoginId(loginId).isPresent()) {
            throw new IllegalStateException("이미 존재하는 로그인 아이디입니다: " + loginId);
        }

        AuthAdmin created = authAdminRepository.save(AuthAdmin.builder()
                .loginId(loginId)
                .password(passwordEncoder.encode(rawPassword))
                .adminId(adminId)
                .build());

        log.info("관리자 계정을 생성했습니다. id={}, loginId={}, adminId={}, status={}",
                created.getId(), created.getLoginId(), created.getAdminId(), created.getStatus());
    }

    /**
     * 회원 도메인 {@code Admins.id}. 토큰 {@code sub}에 담기므로 반드시 실제 값이어야 한다.
     * auth-service는 회원 도메인을 조회하지 않으므로 운영자가 직접 지정한다.
     */
    private Long requireAdminId(ApplicationArguments args) {
        List<String> values = args.getOptionValues(ADMIN_ID_ARG);
        if (values == null || values.isEmpty() || !StringUtils.hasText(values.getFirst())) {
            throw new IllegalArgumentException("--%s 인자가 필요합니다. (회원 도메인 Admins.id)".formatted(ADMIN_ID_ARG));
        }
        try {
            return Long.valueOf(values.getFirst().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--%s 는 숫자여야 합니다.".formatted(ADMIN_ID_ARG), e);
        }
    }

    private String requireLoginId(ApplicationArguments args) {
        List<String> values = args.getOptionValues(LOGIN_ID_ARG);
        if (values == null || values.isEmpty() || !StringUtils.hasText(values.getFirst())) {
            throw new IllegalArgumentException("--%s 인자가 필요합니다.".formatted(LOGIN_ID_ARG));
        }
        String loginId = values.getFirst().trim();
        if (loginId.length() > LOGIN_ID_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "로그인 아이디는 %d자를 넘을 수 없습니다.".formatted(LOGIN_ID_MAX_LENGTH));
        }
        return loginId;
    }

    /** 콘솔이 있으면 입력을 감추고, 파이프로 넘어온 경우 표준입력에서 한 줄을 읽는다. */
    private String readPassword() {
        Console console = System.console();
        if (console != null) {
            char[] entered = console.readPassword("관리자 비밀번호를 입력하세요: ");
            return entered == null ? null : new String(entered);
        }
        try {
            // System.in은 닫지 않는다.
            return new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
        } catch (IOException e) {
            throw new IllegalStateException("비밀번호를 읽지 못했습니다.", e);
        }
    }
}
