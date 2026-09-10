package com.kurly.auth.presentation.controller;

import com.kurly.auth.application.AdminAuthService;
import com.kurly.auth.application.AuthTokenService;
import com.kurly.auth.application.dto.TokenPair;
import com.kurly.auth.infrastructure.security.RefreshTokenCookieFactory;
import com.kurly.auth.presentation.dto.AdminLoginRequest;
import com.kurly.auth.presentation.dto.AdminLoginResponse;
import com.kurly.auth.presentation.dto.TokenRefreshResponse;
import com.kurly.common.exception.UnauthorizedException;
import com.kurly.common.response.ApiResponse;
import com.kurly.common.security.AuthPrincipal;
import com.kurly.common.security.AuthenticatedPrincipal;
import com.kurly.common.security.Authenticated;
import com.kurly.common.security.PublicApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthTokenService authTokenService;
    private final AdminAuthService adminAuthService;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    /**
     * 백오피스 관리자 로그인. access token은 응답 본문으로, refresh token은 HttpOnly 쿠키로 내보낸다
     * (인증인가_설계서 1.4).
     */
    @PublicApi
    @PostMapping("/admin/login")
    public ResponseEntity<ApiResponse<AdminLoginResponse>> adminLogin(@Valid @RequestBody AdminLoginRequest request) {
        TokenPair tokens = adminAuthService.login(request.loginId(), request.password());

        ResponseCookie refreshCookie = refreshTokenCookieFactory.create(
                tokens.refreshToken().token(), tokens.refreshToken().ttl());
        AdminLoginResponse body = new AdminLoginResponse(
                tokens.accessToken().token(), tokens.accessToken().ttl().toSeconds());

        // 본문에 access token이 실리므로 브라우저·중간 캐시에 남지 않게 한다.
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("관리자로그인이 완료되었습니다.", body));
    }

    /**
     * access token 재발급. refresh token은 HttpOnly 쿠키로 전달받고, 회전된 새 쿠키를 응답에 다시 실어 보낸다
     * (인증인가_설계서 1.4·1.5).
     */
    @PublicApi
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(HttpServletRequest request) {
        String refreshToken = refreshTokenCookieFactory.extract(request)
                .orElseThrow(() -> new UnauthorizedException(AuthTokenService.INVALID_REFRESH_TOKEN_MESSAGE));

        TokenPair tokens = authTokenService.refresh(refreshToken);

        ResponseCookie rotatedCookie = refreshTokenCookieFactory.create(
                tokens.refreshToken().token(), tokens.refreshToken().ttl());
        TokenRefreshResponse body = new TokenRefreshResponse(
                tokens.accessToken().token(), tokens.accessToken().ttl().toSeconds());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, rotatedCookie.toString())
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("토큰이 성공적으로 재발급되었습니다.", body));
    }

    /**
     * 로그아웃. 해당 계정의 refresh token 세션을 제거하고 쿠키를 즉시 만료시킨다
     * (인증인가_설계서 1.6).
     */
    @Authenticated
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Map<String, Object>>> logout(@AuthPrincipal AuthenticatedPrincipal me) {
        authTokenService.logout(me.userId(), me.role());

        // 쿠키는 로그아웃 요청에 실려오지 않지만, 같은 Path로 만료 쿠키를 내려보내면 브라우저가 삭제한다.
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.expire().toString())
                // 명세가 data를 빈 객체로 정의한다.
                .body(ApiResponse.success("성공적으로 로그아웃 되었습니다.", Map.<String, Object>of()));
    }
}
