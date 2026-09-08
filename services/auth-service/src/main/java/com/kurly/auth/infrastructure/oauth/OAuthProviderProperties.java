package com.kurly.auth.infrastructure.oauth;

import com.kurly.auth.domain.enums.AuthProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * 소셜 제공자 설정.
 *
 * @param allowedRedirectUris 허용할 redirect_uri 목록. <b>반드시 검증해야 한다</b> —
 *                            공격자가 임의 redirect_uri를 지정하면 인가 코드를 자기 서버로 받아갈 수 있다(BE-16).
 * @param providers           제공자별 엔드포인트·자격증명. 엔드포인트는 각 제공자 문서 기준으로 확인이 필요하다.
 */
@ConfigurationProperties(prefix = "oauth")
public record OAuthProviderProperties(
        List<String> allowedRedirectUris,
        Map<AuthProvider, Provider> providers
) {

    public Provider require(AuthProvider provider) {
        Provider found = providers == null ? null : providers.get(provider);
        if (found == null) {
            throw new IllegalStateException("소셜 제공자 설정이 없습니다: " + provider);
        }
        return found;
    }

    public boolean isAllowedRedirectUri(String redirectUri) {
        return allowedRedirectUris != null && allowedRedirectUris.contains(redirectUri);
    }

    /**
     * @param userIdPath         사용자 정보 응답에서 식별자를 꺼낼 경로. 점으로 중첩을 표현한다.
     *                           카카오는 {@code id}, 네이버는 {@code response.id}.
     * @param pkceEnabled        PKCE 파라미터 전송 여부. 제공자가 지원하지 않으면 꺼야 한다.
     *                           지원하지 않는데 보내면 대개 무시되지만, 보호 효과는 없다.
     * @param tokenRequestMethod 토큰 요청 방식. 카카오는 POST 폼, 네이버 문서는 GET 쿼리스트링이다.
     */
    public record Provider(
            String clientId,
            String clientSecret,
            String authorizeUri,
            String tokenUri,
            String userInfoUri,
            String scope,
            String userIdPath,
            Boolean pkceEnabled,
            TokenRequestMethod tokenRequestMethod
    ) {

        public Provider {
            pkceEnabled = pkceEnabled == null || pkceEnabled;
            tokenRequestMethod = tokenRequestMethod == null ? TokenRequestMethod.POST : tokenRequestMethod;
        }
    }

    public enum TokenRequestMethod {
        GET,
        POST
    }
}
