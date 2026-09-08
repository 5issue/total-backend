package com.kurly.auth.infrastructure.client;

import com.kurly.auth.application.port.UserProfileClient;
import com.kurly.auth.domain.enums.AuthProvider;
import com.kurly.common.exception.BusinessException;
import com.kurly.common.exception.GlobalErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * user-service의 {@code POST /internal/v1/users/sync-profile} 호출.
 *
 * <p>내부 API라 사용자 토큰 없이 호출한다. 접근 통제는 NetworkPolicy가 담당한다(인증인가_설계서 3.2·3.3).
 */
@Slf4j
@Component
public class UserServiceProfileClient implements UserProfileClient {

    private static final String SYNC_PROFILE_PATH = "/internal/v1/users/sync-profile";

    private final RestClient restClient;

    public UserServiceProfileClient(@Value("${user-service.base-url}") String baseUrl) {
        this.restClient = OutboundRestClients.builder().baseUrl(baseUrl).build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public SyncedProfile syncProfile(AuthProvider provider, String providerId) {
        Map<String, Object> body;
        try {
            body = restClient.post()
                    .uri(SYNC_PROFILE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("provider", provider.name(), "providerId", providerId))
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<>() {
                    });
        } catch (Exception e) {
            // 회원 프로필을 못 만들면 user_id를 알 수 없어 토큰을 발급할 수 없다.
            log.error("user-service 프로필 동기화 실패: provider={}", provider, e);
            throw new BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR,
                    "회원 정보 처리 중 오류가 발생했습니다.");
        }

        Object data = body == null ? null : body.get("data");
        if (!(data instanceof Map<?, ?> map) || map.get("userId") == null) {
            log.error("user-service 응답에 userId가 없음: body={}", body);
            throw new BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR,
                    "회원 정보 처리 중 오류가 발생했습니다.");
        }
        Map<String, Object> profile = (Map<String, Object>) map;
        return new SyncedProfile(
                Long.valueOf(profile.get("userId").toString()),
                Boolean.parseBoolean(String.valueOf(profile.getOrDefault("isNewUser", false))));
    }
}
