package com.kurly.auth.presentation.controller;

import com.kurly.auth.application.SocialAuthService;
import com.kurly.auth.application.dto.SocialLoginResult;
import com.kurly.auth.domain.enums.AuthProvider;
import com.kurly.auth.exception.AuthErrorCode;
import com.kurly.auth.infrastructure.oauth.OAuthTransaction;
import com.kurly.auth.infrastructure.oauth.OAuthTransactionCookies;
import com.kurly.auth.infrastructure.security.RefreshTokenCookieFactory;
import com.kurly.auth.presentation.dto.SocialLoginUrlRequest;
import com.kurly.auth.presentation.dto.SocialLoginUrlResponse;
import com.kurly.common.exception.BusinessException;
import com.kurly.common.exception.UnauthorizedException;
import com.kurly.common.response.ApiResponse;
import com.kurly.common.security.PublicApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Locale;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth/oauth")
public class OAuthController {

    /** 프론트가 로그인 결과를 구분하는 쿼리 파라미터. 토큰은 절대 싣지 않는다. */
    private static final String RESULT_PARAM = "login";
    private static final String RESULT_SUCCESS = "success";
    private static final String RESULT_FAILED = "failed";
    private static final String RETURN_TO_PARAM = "returnTo";

    private final SocialAuthService socialAuthService;
    private final OAuthTransactionCookies oAuthTransactionCookies;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;
    private final String frontendRedirectUri;

    public OAuthController(SocialAuthService socialAuthService,
                           OAuthTransactionCookies oAuthTransactionCookies,
                           RefreshTokenCookieFactory refreshTokenCookieFactory,
                           @Value("${oauth.frontend-redirect-uri}") String frontendRedirectUri) {
        this.socialAuthService = socialAuthService;
        this.oAuthTransactionCookies = oAuthTransactionCookies;
        this.refreshTokenCookieFactory = refreshTokenCookieFactory;
        this.frontendRedirectUri = frontendRedirectUri;
    }

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
                socialAuthService.createAuthorizationRequest(
                        authProvider, request.redirectUri(), request.returnTo());

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        oAuthTransactionCookies.create(authorization.transaction())
                .forEach(cookie -> builder.header(HttpHeaders.SET_COOKIE, cookie.toString()));

        return builder.body(ApiResponse.success("소셜 로그인 URL",
                new SocialLoginUrlResponse(authorization.loginUrl(), authProvider.name())));
    }

    /**
     * 소셜 콜백. 제공자가 브라우저를 직접 보내오는 <b>최상위 내비게이션</b>이므로 JSON이 아니라
     * 프론트로 302 리다이렉트한다. 성공·실패를 모두 리다이렉트해야 사용자가 JSON 화면에 갇히지 않는다.
     *
     * <p>access token은 URL에 싣지 않는다(인증인가_설계서 1.4 — access는 메모리 보관).
     * refresh 쿠키만 내려보내고, 프론트가 {@code POST /api/v1/auth/refresh}로 access token을 받아간다.
     *
     * <p>{@code code}·{@code state}를 필수로 선언하면 사용자가 동의를 취소했을 때
     * 파라미터 누락 400이 JSON으로 나간다. 그래서 선택으로 두고 직접 검사한다.
     */
    @PublicApi
    @GetMapping("/{provider}/callback")
    public ResponseEntity<Void> callback(
            @PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletRequest httpRequest) {

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.FOUND);
        // 인가 컨텍스트는 1회용이므로 성패와 무관하게 즉시 만료시킨다.
        oAuthTransactionCookies.expire()
                .forEach(cookie -> builder.header(HttpHeaders.SET_COOKIE, cookie.toString()));

        if (StringUtils.hasText(error) || !StringUtils.hasText(code) || !StringUtils.hasText(state)) {
            // 사용자가 동의를 거부하면 제공자가 code 대신 error를 싣고 되돌려보낸다.
            log.info("인가 코드 없는 콜백: provider={}, error={}", provider, error);
            return redirect(builder, RESULT_FAILED);
        }

        try {
            OAuthTransaction transaction = oAuthTransactionCookies.extract(httpRequest)
                    .orElseThrow(() -> new UnauthorizedException(SocialAuthService.INVALID_AUTH_CODE_MESSAGE));
            SocialLoginResult result =
                    socialAuthService.handleCallback(parseProvider(provider), code, state, transaction);

            ResponseCookie refreshCookie = refreshTokenCookieFactory.create(
                    result.tokens().refreshToken().token(), result.tokens().refreshToken().ttl());
            builder.header(HttpHeaders.SET_COOKIE, refreshCookie.toString());

            return redirect(builder, RESULT_SUCCESS, transaction.returnTo());
        } catch (BusinessException e) {
            log.warn("소셜 콜백 처리 실패: provider={}, errorCode={}", provider, e.getErrorCode());
            return redirect(builder, RESULT_FAILED);
        } catch (RuntimeException e) {
            log.error("소셜 콜백 처리 중 예기치 못한 오류: provider={}", provider, e);
            return redirect(builder, RESULT_FAILED);
        }
    }

    /**
     * 리다이렉트 대상은 <b>설정값에서만</b> 온다. 요청에서 받은 값을 쓰면 오픈 리다이렉트가 된다.
     * 시큐어코딩가이드에 해당 항목이 없어 보안팀에 확인 요청 중이다(검토요청서 A-8).
     */
    private ResponseEntity<Void> redirect(ResponseEntity.BodyBuilder builder, String result) {
        return redirect(builder, result, null);
    }

    private ResponseEntity<Void> redirect(ResponseEntity.BodyBuilder builder, String result, String returnTo) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(frontendRedirectUri)
                .queryParam(RESULT_PARAM, result);
        if (StringUtils.hasText(returnTo)) {
            // 이미 검증을 마친 값이다. queryParam이 여기서 딱 한 번 인코딩한다.
            uri.queryParam(RETURN_TO_PARAM, returnTo);
        }
        String location = uri.toUriString();
        return builder
                .location(URI.create(location))
                .cacheControl(CacheControl.noStore())
                .build();
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
