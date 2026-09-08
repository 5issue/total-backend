package com.kurly.auth.infrastructure.oauth;

import com.kurly.auth.domain.enums.AuthProvider;
import com.kurly.auth.exception.AuthErrorCode;
import com.kurly.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import com.kurly.auth.infrastructure.client.OutboundRestClients;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 소셜 제공자와의 통신. 인가 URL 생성, 인가 코드 교환, 사용자 식별자 조회를 담당한다.
 *
 * <p>엔드포인트·파라미터는 제공자 문서에 따라 달라지므로 모두 설정값으로 두었다.
 */
@Slf4j
@Component
public class OAuthClient {

    private final RestClient restClient;
    private final OAuthProviderProperties properties;

    public OAuthClient(OAuthProviderProperties properties) {
        this.restClient = OutboundRestClients.builder().build();
        this.properties = properties;
    }

    public String buildAuthorizationUri(AuthProvider provider, OAuthTransaction transaction, String challenge) {
        OAuthProviderProperties.Provider config = properties.require(provider);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("response_type", "code");
        params.add("client_id", config.clientId());
        params.add("redirect_uri", transaction.redirectUri());
        params.add("state", transaction.state());
        if (config.pkceEnabled()) {
            params.add("code_challenge", challenge);
            params.add("code_challenge_method", PkceChallenge.CHALLENGE_METHOD);
        }
        if (StringUtils.hasText(config.scope())) {
            params.add("scope", config.scope());
        }
        return config.authorizeUri() + "?" + toEncodedQuery(params);
    }

    /**
     * OAuth 파라미터는 {@code application/x-www-form-urlencoded} 규격이므로 값을 전부 인코딩한다.
     *
     * <p>{@code UriComponentsBuilder.encode()}로는 부족하다. RFC 3986상 {@code :}와 {@code /}는
     * 쿼리 컴포넌트에서 합법이라 인코딩되지 않아 {@code redirect_uri}가 원문 그대로 실린다.
     * 네이버 가이드는 {@code redirect_uri}·{@code state}에 URL 인코딩 적용을 명시한다.
     */
    private String toEncodedQuery(MultiValueMap<String, String> params) {
        StringBuilder query = new StringBuilder();
        params.forEach((key, values) -> values.forEach(value -> {
            if (!query.isEmpty()) {
                query.append('&');
            }
            query.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8));
        }));
        return query.toString();
    }

    /** 인가 코드를 제공자 access token으로 교환한다. code_verifier는 여기서만 전송된다. */
    public String exchangeCodeForAccessToken(AuthProvider provider, String code, OAuthTransaction transaction) {
        OAuthProviderProperties.Provider config = properties.require(provider);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", config.clientId());
        form.add("redirect_uri", transaction.redirectUri());
        form.add("code", code);
        // 네이버는 토큰 요청에도 state를 요구한다.
        form.add("state", transaction.state());
        if (config.pkceEnabled()) {
            form.add("code_verifier", transaction.codeVerifier());
        }
        if (StringUtils.hasText(config.clientSecret())) {
            form.add("client_secret", config.clientSecret());
        }

        Map<String, Object> response;
        try {
            response = requestToken(config, form);
        } catch (Exception e) {
            log.warn("소셜 토큰 교환 실패: provider={}", provider, e);
            throw new BusinessException(AuthErrorCode.INVALID_AUTH_CODE, AuthErrorCode.INVALID_AUTH_CODE.getMessage());
        }

        Object accessToken = response == null ? null : response.get("access_token");
        if (accessToken == null) {
            log.warn("소셜 토큰 응답에 access_token이 없음: provider={}", provider);
            throw new BusinessException(AuthErrorCode.INVALID_AUTH_CODE, AuthErrorCode.INVALID_AUTH_CODE.getMessage());
        }
        return accessToken.toString();
    }

    private Map<String, Object> requestToken(OAuthProviderProperties.Provider config,
                                             MultiValueMap<String, String> form) {
        if (config.tokenRequestMethod() == OAuthProviderProperties.TokenRequestMethod.GET) {
            String uri = config.tokenUri() + "?" + toEncodedQuery(form);
            return restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<>() {
                    });
        }
        return restClient.post()
                .uri(config.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<>() {
                });
    }

    /** 제공자 access token으로 사용자 고유 식별자를 조회한다. 개인정보는 가져오지 않는다. */
    public String fetchProviderId(AuthProvider provider, String providerAccessToken) {
        OAuthProviderProperties.Provider config = properties.require(provider);

        Map<String, Object> response;
        try {
            response = restClient.get()
                    .uri(config.userInfoUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerAccessToken)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<>() {
                    });
        } catch (Exception e) {
            log.warn("소셜 사용자 정보 조회 실패: provider={}", provider, e);
            throw new BusinessException(AuthErrorCode.INVALID_AUTH_CODE, AuthErrorCode.INVALID_AUTH_CODE.getMessage());
        }

        Object value = extract(response, config.userIdPath());
        if (value == null) {
            log.warn("소셜 사용자 정보에서 식별자를 찾지 못함: provider={}, path={}", provider, config.userIdPath());
            throw new BusinessException(AuthErrorCode.INVALID_AUTH_CODE, AuthErrorCode.INVALID_AUTH_CODE.getMessage());
        }
        return value.toString();
    }

    /** 점으로 구분된 경로로 중첩 응답에서 값을 꺼낸다. 카카오는 {@code id}, 네이버는 {@code response.id}. */
    @SuppressWarnings("unchecked")
    private Object extract(Map<String, Object> response, String path) {
        Object current = response;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(segment);
        }
        return current;
    }
}
