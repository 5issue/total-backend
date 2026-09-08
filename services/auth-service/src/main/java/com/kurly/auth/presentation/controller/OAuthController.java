package com.kurly.auth.presentation.controller;

import com.kurly.auth.application.SocialAuthService;
import com.kurly.auth.application.dto.SocialLoginResult;
import com.kurly.auth.domain.enums.AuthProvider;
import com.kurly.auth.exception.AuthErrorCode;
import com.kurly.auth.infrastructure.oauth.OAuthTransaction;
import com.kurly.auth.infrastructure.oauth.OAuthTransactionCookies;
import com.kurly.auth.infrastructure.security.RefreshTokenCookieFactory;
import com.kurly.auth.presentation.dto.SocialLoginResponse;
import com.kurly.auth.presentation.dto.SocialLoginUrlRequest;
import com.kurly.auth.presentation.dto.SocialLoginUrlResponse;
import com.kurly.common.exception.BusinessException;
import com.kurly.common.exception.UnauthorizedException;
import com.kurly.common.response.ApiResponse;
import com.kurly.common.security.PublicApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/v1/auth/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final SocialAuthService socialAuthService;
    private final OAuthTransactionCookies oAuthTransactionCookies;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    /**
     * 소셜 인가 URL 발급. {@code state}·{@code code_verifier}는 콜백까지 이어져야 하므로
     * HttpOnly 쿠키로 함께 내려보낸다.
     */
    @PublicApi
    @PostMapping("/{provider}")
    public ResponseEntity<ApiResponse<SocialLoginUrlResponse>> loginUrl(
            @PathVariable String provider,
            @Valid @RequestBody SocialLoginUrlRequest request) {

        AuthProvider authProvider = parseProvider(provider);
        SocialAuthService.AuthorizationRequest authorization =
                socialAuthService.createAuthorizationRequest(authProvider, request.redirectUri());

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        oAuthTransactionCookies.create(authorization.transaction())
                .forEach(cookie -> builder.header(HttpHeaders.SET_COOKIE, cookie.toString()));

        return builder.body(ApiResponse.success("소셜 로그인 URL",
                new SocialLoginUrlResponse(authorization.loginUrl(), authProvider.name())));
    }

    /**
     * 소셜 콜백. 최초 로그인은 회원가입을 겸하므로 201, 기존 회원은 200으로 응답한다.
     */
    @PublicApi
    @GetMapping("/{provider}/callback")
    public ResponseEntity<ApiResponse<SocialLoginResponse>> callback(
            @PathVariable String provider,
            @RequestParam String code,
            @RequestParam String state,
            HttpServletRequest httpRequest) {

        AuthProvider authProvider = parseProvider(provider);
        OAuthTransaction transaction = oAuthTransactionCookies.extract(httpRequest)
                .orElseThrow(() -> new UnauthorizedException(SocialAuthService.INVALID_AUTH_CODE_MESSAGE));

        SocialLoginResult result = socialAuthService.handleCallback(authProvider, code, state, transaction);

        ResponseEntity.BodyBuilder builder =
                ResponseEntity.status(result.newUser() ? HttpStatus.CREATED : HttpStatus.OK);
        // 인가 컨텍스트는 1회용이므로 즉시 만료시킨다.
        oAuthTransactionCookies.expire()
                .forEach(cookie -> builder.header(HttpHeaders.SET_COOKIE, cookie.toString()));

        ResponseCookie refreshCookie = refreshTokenCookieFactory.create(
                result.tokens().refreshToken().token(), result.tokens().refreshToken().ttl());
        builder.header(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        SocialLoginResponse body = new SocialLoginResponse(
                result.tokens().accessToken().token(),
                result.tokens().accessToken().ttl().toSeconds(),
                new SocialLoginResponse.UserSummary(result.userId()));

        return builder.body(ApiResponse.success("소셜 로그인이 완료되었습니다.", body));
    }

    private AuthProvider parseProvider(String provider) {
        try {
            return AuthProvider.valueOf(provider.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(AuthErrorCode.BAD_REQUEST,
                    SocialAuthService.UNSUPPORTED_PROVIDER_MESSAGE);
        }
    }
}
