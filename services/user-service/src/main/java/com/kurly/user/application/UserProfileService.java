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
     *
     * <p><b>트랜잭션을 걸지 않는다.</b> 걸면 제약 위반이 그 트랜잭션을 망가뜨린 뒤에 재조회를
     * 수행하게 되어, 세션이 이미 깨진 상태라 조회가 실패한다("has a null identifier"). 실제로
     * 동시 요청 시 한쪽이 그렇게 실패했다. 저장소 호출마다 각자의 트랜잭션을 쓰면, 실패한 저장의
     * 트랜잭션만 롤백되고 이어지는 재조회는 <b>새 트랜잭션에서</b> 먼저 커밋된 행을 볼 수 있다.
     *
     * <p>여러 쓰기를 한 단위로 묶을 필요가 없어 트랜잭션 경계가 없어도 무방하다.
     */
    public SyncResult syncProfile(AuthProvider provider, String providerId, String name) {
        return userRepository.findByProviderAndProviderId(provider, providerId)
                .map(existing -> new SyncResult(fillNameIfBlank(existing, name), false))
                .orElseGet(() -> create(provider, providerId, name));
    }

    /**
     * 기존 회원의 비어 있는 이름을 채운다.
     *
     * <p><b>{@code save}로 하지 않는다.</b> 이 클래스는 트랜잭션을 걸지 않으므로(위 주석 참고)
     * 조회해 온 엔티티는 준영속이고, 그 상태의 {@code save}는 merge라서 <b>전 컬럼을 UPDATE</b>한다.
     * 그러면 두 가지가 깨진다.
     * <ol>
     *   <li>동시 요청이 모두 "이름이 비어 있음"을 본 뒤 각자 저장해 나중 것이 앞의 것을 덮어쓴다</li>
     *   <li>읽어온 뒤 다른 트랜잭션이 바꾼 {@code status}·{@code email}까지 낡은 값으로 되돌린다</li>
     * </ol>
     * 그래서 판단과 갱신을 DB의 한 문장으로 묶고, {@code name} 컬럼만 건드린다.
     *
     * <p>반환하는 엔티티의 메모리 값도 맞춰 둔다. 응답에 이름이 실리지는 않지만, 갱신에 성공한
     * 객체가 옛 값을 들고 있으면 호출부가 오해한다. 경합에서 졌으면(0행) 건드리지 않는다.
     */
    private User fillNameIfBlank(User existing, String name) {
        if (name == null || name.isBlank()) {
            return existing;
        }
        if (existing.getName() != null && !existing.getName().isBlank()) {
            // 방금 읽은 값에 이름이 있으면 DB에도 있다. 매 로그인마다 0행 UPDATE를 날릴 이유가 없다.
            // 어디까지나 비용 절약이고, 덮어쓰기를 막는 보증은 아래 조건부 UPDATE가 한다.
            return existing;
        }
        if (userRepository.fillNameIfBlank(existing.getId(), name.strip()) == 0) {
            // 이미 이름이 있거나 동시 요청이 먼저 채웠다. 둘 다 정상이다.
            return existing;
        }
        existing.fillNameIfBlank(name);
        log.info("회원 이름 보정: userId={}", existing.getId());
        return existing;
    }

    private SyncResult create(AuthProvider provider, String providerId, String name) {
        try {
            User created = userRepository.save(User.builder()
                    .provider(provider)
                    .providerId(providerId)
                    .name(name)
                    .build());
            log.info("회원 프로필 생성: userId={}, provider={}", created.getId(), provider);
            return new SyncResult(created, true);
        } catch (DataIntegrityViolationException e) {
            // 동시 요청이 먼저 만들었다. 그쪽 결과를 그대로 쓴다.
            log.info("동시 생성 감지, 기존 회원을 사용한다: provider={}", provider);
            User existing = userRepository.findByProviderAndProviderId(provider, providerId)
                    .orElseThrow(() -> e);
            return new SyncResult(fillNameIfBlank(existing, name), false);
        }
    }

    public record SyncResult(User user, boolean newUser) {
    }
}
