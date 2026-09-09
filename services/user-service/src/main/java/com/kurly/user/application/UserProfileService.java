package com.kurly.user.application;

import com.kurly.common.exception.GlobalErrorCode;
import com.kurly.common.exception.UnauthorizedException;
import com.kurly.user.domain.entity.User;
import com.kurly.user.domain.enums.AuthProvider;
import com.kurly.user.domain.repository.UserRepository;
import com.kurly.user.presentation.dto.DefaultAddressResponse;
import com.kurly.user.presentation.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 프로필 — 소셜 로그인 시 auth-service가 호출하는 동기화와, 주문자 정보 조회.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final DeliveryAddressService deliveryAddressService;

    /**
     * 주문자 정보와 기본 배송지를 함께 조회한다.
     *
     * <p>토큰의 {@code sub}가 가리키는 회원이 없으면 401로 처리한다. 탈퇴 등으로 사라진 주체의
     * 토큰이므로 인증이 성립하지 않으며, 실패 사유를 더 드러내지 않는다(시큐어코딩가이드 BE-17).
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException(GlobalErrorCode.UNAUTHORIZED.getMessage()));

        return new UserProfileResponse(
                user.getName(),
                deliveryAddressService.findDefault(userId)
                        .map(DefaultAddressResponse::from)
                        .orElse(null));
    }

    /**
     * 소셜 식별자로 회원을 찾고 없으면 만든다. <b>멱등하다</b> — auth-service가 재시도해도
     * 회원이 중복 생성되지 않는다.
     *
     * <p>조회 후 생성 사이에 동시 요청이 끼어들 수 있으므로, 유니크 제약 위반을 잡아
     * 재조회하는 경로를 둔다. 애플리케이션 조회만으로는 동시성을 막지 못한다.
     */
    @Transactional
    public SyncResult syncProfile(AuthProvider provider, String providerId) {
        return userRepository.findByProviderAndProviderId(provider, providerId)
                .map(existing -> new SyncResult(existing, false))
                .orElseGet(() -> create(provider, providerId));
    }

    private SyncResult create(AuthProvider provider, String providerId) {
        try {
            User created = userRepository.save(User.builder()
                    .provider(provider)
                    .providerId(providerId)
                    .build());
            log.info("회원 프로필 생성: userId={}, provider={}", created.getId(), provider);
            return new SyncResult(created, true);
        } catch (DataIntegrityViolationException e) {
            // 동시 요청이 먼저 만들었다. 그쪽 결과를 그대로 쓴다.
            log.info("동시 생성 감지, 기존 회원을 사용한다: provider={}", provider);
            User existing = userRepository.findByProviderAndProviderId(provider, providerId)
                    .orElseThrow(() -> e);
            return new SyncResult(existing, false);
        }
    }

    public record SyncResult(User user, boolean newUser) {
    }
}
