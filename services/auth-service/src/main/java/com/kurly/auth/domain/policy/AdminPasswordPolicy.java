package com.kurly.auth.domain.policy;

import com.kurly.common.exception.InvalidValueException;

import java.nio.charset.StandardCharsets;

/**
 * 관리자 비밀번호 정책. 시큐어코딩가이드 BE-07 — 비밀번호 정책은 서버에서 검증한다.
 *
 * <p>구체적 기준은 계정 정책이 확정되면 조정한다. 현재 값은 잠정이다.
 */
public final class AdminPasswordPolicy {

    static final int MIN_LENGTH = 12;

    /**
     * BCrypt는 입력이 72바이트를 넘으면 잘라내고 비교한다. 초과분이 검증에서 무시되어
     * 실제보다 짧은 비밀번호가 되므로 애초에 거부한다.
     */
    static final int MAX_BYTES = 72;

    private AdminPasswordPolicy() {
    }

    public static void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new InvalidValueException("비밀번호를 입력해야 합니다.");
        }
        if (rawPassword.length() < MIN_LENGTH) {
            throw new InvalidValueException("비밀번호는 %d자 이상이어야 합니다.".formatted(MIN_LENGTH));
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new InvalidValueException("비밀번호는 UTF-8 기준 %d바이트를 넘을 수 없습니다.".formatted(MAX_BYTES));
        }
        if (!hasLetter(rawPassword) || !hasDigit(rawPassword) || !hasSpecial(rawPassword)) {
            throw new InvalidValueException("비밀번호는 영문·숫자·특수문자를 모두 포함해야 합니다.");
        }
    }

    private static boolean hasLetter(String value) {
        return value.chars().anyMatch(Character::isLetter);
    }

    private static boolean hasDigit(String value) {
        return value.chars().anyMatch(Character::isDigit);
    }

    private static boolean hasSpecial(String value) {
        return value.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch));
    }
}
