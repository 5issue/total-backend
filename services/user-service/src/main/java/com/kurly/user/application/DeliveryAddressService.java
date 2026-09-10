package com.kurly.user.application;

import com.kurly.common.exception.GlobalErrorCode;
import com.kurly.common.exception.UnauthorizedException;
import com.kurly.user.domain.entity.DeliveryAddress;
import com.kurly.user.domain.repository.DeliveryAddressRepository;
import com.kurly.user.domain.repository.UserRepository;
import com.kurly.user.exception.AddressNotFoundException;
import com.kurly.user.presentation.dto.CreateAddressRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 배송지 조회·등록·기본 배송지 지정.
 *
 * <p>소유권 검사는 공통 인증 처리기가 아니라 이 계층의 책임이다. 모든 조회에
 * {@code userId}를 조건으로 함께 걸어, 타인의 배송지는 애초에 결과에 잡히지 않게 한다.
 */
@Service
@RequiredArgsConstructor
public class DeliveryAddressService {

    private final DeliveryAddressRepository deliveryAddressRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<DeliveryAddress> findAll(Long userId) {
        return deliveryAddressRepository.findAllByUserIdOrderByDefaultAddressDescIdDesc(userId);
    }

    @Transactional(readOnly = true)
    public Optional<DeliveryAddress> findDefault(Long userId) {
        return deliveryAddressRepository.findAllByUserIdAndDefaultAddressTrue(userId).stream().findFirst();
    }

    /**
     * 배송지를 등록한다.
     *
     * <p>첫 배송지는 요청값과 무관하게 기본 배송지가 된다. 그렇지 않으면 주문 프로필의
     * 기본 배송지가 계속 비어 있게 된다.
     */
    @Transactional
    public DeliveryAddress create(Long userId, CreateAddressRequest request) {
        lockUser(userId);

        boolean first = deliveryAddressRepository.countByUserId(userId) == 0;
        boolean makeDefault = first || Boolean.TRUE.equals(request.isDefault());

        if (makeDefault) {
            deliveryAddressRepository.clearDefaultOf(userId);
        }

        return deliveryAddressRepository.save(DeliveryAddress.builder()
                .userId(userId)
                .addressName(request.addressName())
                .recipientName(request.recipientName())
                .phone(request.phone())
                .zipCode(request.zipCode())
                .address(request.address())
                .addressDetail(request.addressDetail())
                .defaultAddress(makeDefault)
                .accessMethod(request.accessMethod())
                .build());
    }

    /**
     * 기본 배송지를 옮긴다.
     *
     * <p>없는 배송지와 타인의 배송지를 같은 예외로 처리한다. 구분해 응답하면 ID를 훑어
     * 남의 배송지 존재 여부를 알아낼 수 있다.
     */
    @Transactional
    public DeliveryAddress setDefault(Long userId, Long addressId) {
        lockUser(userId);

        DeliveryAddress target = deliveryAddressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(AddressNotFoundException::new);

        // 이미 기본이면 아무것도 하지 않는다. 해제 후 다시 지정하면 더티 체킹이 "변경 없음"으로
        // 판단해 갱신을 생략하고, 벌크 해제만 반영되어 기본 배송지가 사라진다.
        if (target.isDefaultAddress()) {
            return target;
        }

        deliveryAddressRepository.clearDefaultOf(userId);
        target.markDefault();
        return target;
    }

    /**
     * 회원 행을 잠가 같은 회원의 기본 배송지 전환을 직렬화한다.
     *
     * <p>잠그지 않으면 두 요청이 각자 기존 기본을 해제한 뒤 서로 다른 배송지를 기본으로 저장해
     * 기본 배송지가 둘이 된다. DB 유니크 제약이 최종 방어선이지만, 그것만 두면 둘 중 하나가
     * 제약 위반으로 실패한다. 잠금은 그 실패를 대기로 바꾼다.
     */
    private void lockUser(Long userId) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UnauthorizedException(GlobalErrorCode.UNAUTHORIZED.getMessage()));
    }
}
