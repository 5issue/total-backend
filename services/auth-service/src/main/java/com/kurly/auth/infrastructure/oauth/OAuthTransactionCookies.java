package com.kurly.auth.infrastructure.oauth;

import com.kurly.auth.infrastructure.security.RefreshTokenCookieProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 인가 요청 컨텍스트({@code state}·{@code code_verifier}·{@code redirect_uri})를 HttpOnly 쿠키로 오간다.
 *
 * <p><b>SameSite가 Lax인 이유</b>: 소셜 제공자에서 우리 쪽으로 돌아오는 흐름은 크로스사이트 이동이라
 * refresh 쿠키에 쓰는 {@code Strict}로 두면 쿠키가 전송되지 않아 콜백에서 검증할 값을 읽지 못한다.
 *
 * <p>수명은 짧게 잡고 콜백에서 즉시 만료시킨다.
 */
@Component
@RequiredArgsConstructor
public class OAuthTransactionCookies {

    static final String STATE_COOKIE = "oauth_state";
    static final String VERIFIER_COOKIE = "oauth_code_verifier";
    static final String REDIRECT_URI_COOKIE = "oauth_redirect_uri";

    private static final String PATH = "/api/v1/auth/oauth";
    private static final Duration TTL = Duration.ofMinutes(10);

    /** Secure 여부는 refresh 쿠키 설정을 따라가 로컬 http 테스트에서 함께 내려갈 수 있게 한다. */
    private final RefreshTokenCookieProperties cookieProperties;

    public List<ResponseCookie> create(OAuthTransaction transaction) {
        List<ResponseCookie> cookies = new ArrayList<>();
        cookies.add(build(STATE_COOKIE, transaction.state(), TTL));
        cookies.add(build(VERIFIER_COOKIE, transaction.codeVerifier(), TTL));
        cookies.add(build(REDIRECT_URI_COOKIE, transaction.redirectUri(), TTL));
        return cookies;
    }

    public List<ResponseCookie> expire() {
        return List.of(
                build(STATE_COOKIE, "", Duration.ZERO),
                build(VERIFIER_COOKIE, "", Duration.ZERO),
                build(REDIRECT_URI_COOKIE, "", Duration.ZERO));
    }

    public Optional<OAuthTransaction> extract(HttpServletRequest request) {
        Optional<String> state = read(request, STATE_COOKIE);
        Optional<String> verifier = read(request, VERIFIER_COOKIE);
        Optional<String> redirectUri = read(request, REDIRECT_URI_COOKIE);
        if (state.isEmpty() || verifier.isEmpty() || redirectUri.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new OAuthTransaction(state.get(), verifier.get(), redirectUri.get()));
    }

    private Optional<String> read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(StringUtils::hasText)
                .findFirst();
    }

    private ResponseCookie build(String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite("Lax")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }
}
