package com.kurly.auth.infrastructure.oauth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 로그인 후 복귀 경로({@code returnTo}) 검증. <b>내부 상대 경로만 통과시킨다.</b>
 *
 * <p>검증 규칙은 보안팀 확인 사항이다(검토요청서 A-8). 시큐어코딩가이드에 오픈 리다이렉트
 * 항목이 없어 별도로 확인받았다.
 *
 * <p><b>왜 거절하지 않고 비우는가</b>: {@code returnTo}는 편의 기능이라, 값이 이상하다고 해서
 * 로그인 자체를 막으면 안 된다. 통과하지 못하면 빈 값을 돌려주고 호출측이 기본 경로로 보낸다.
 *
 * <p><b>쿠키에서 읽을 때도 다시 검증한다.</b> HttpOnly는 스크립트 접근만 막을 뿐,
 * 사용자가 개발자도구나 프록시로 쿠키 값을 바꿀 수 있어 신뢰할 수 있는 입력이 아니다.
 */
@Slf4j
public final class ReturnToPath {

    private static final int MAX_LENGTH = 512;

    private ReturnToPath() {
    }

    /**
     * 퍼센트 인코딩을 푼 뒤 검증하고, <b>검증한 값을 그대로</b> 돌려준다.
     * 되돌려줄 때 한 번만 인코딩되도록 디코딩된 형태를 보관한다.
     */
    public static Optional<String> sanitize(String raw) {
        if (!StringUtils.hasText(raw) || raw.length() > MAX_LENGTH) {
            return Optional.empty();
        }

        String decoded;
        try {
            decoded = UriUtils.decode(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // 깨진 퍼센트 시퀀스
            return Optional.empty();
        }

        if (decoded.length() > MAX_LENGTH || !isInternalPath(decoded)) {
            log.warn("허용되지 않은 returnTo를 버리고 기본 경로로 보낸다");
            return Optional.empty();
        }
        return Optional.of(decoded);
    }

    private static boolean isInternalPath(String value) {
        // 절대 경로여야 한다.
        if (!value.startsWith("/")) {
            return false;
        }
        // "//evil.com"은 스킴 상대 URL이라 브라우저가 외부 도메인으로 해석한다.
        if (value.startsWith("//")) {
            return false;
        }
        // 역슬래시를 전면 차단하면 "/\evil.com" 류 우회도 함께 막힌다.
        if (value.indexOf('\\') >= 0) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                return false;
            }
        }

        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            return false;
        }
        // 스킴·호스트가 붙으면 내부 경로가 아니다.
        if (uri.isAbsolute() || uri.getScheme() != null
                || uri.getAuthority() != null || uri.getHost() != null) {
            return false;
        }
        // 프론트가 해시를 쓰지 않기로 확정했다. 받지 않으면 인코딩을 신경 쓸 일도 없다.
        if (uri.getFragment() != null) {
            return false;
        }

        String path = uri.getPath();
        if (path == null || path.isEmpty()) {
            return false;
        }
        for (String segment : path.split("/", -1)) {
            if ("..".equals(segment)) {
                return false;
            }
        }
        return true;
    }
}
