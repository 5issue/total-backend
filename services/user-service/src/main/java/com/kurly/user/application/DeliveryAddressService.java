package com.kurly.user.application;

import com.kurly.user.domain.entity.DeliveryAddress;
import com.kurly.user.domain.repository.DeliveryAddressRepository;
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
        boolean first = deliveryAddressRepository.countByUserId(userId) == 0;
        boolean makeDefault = first || Boolean.TRUE.equals(request.isDefault());

        if (makeDefault) {
            clearDefault(userId);
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
        DeliveryAddress target = deliveryAddressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(AddressNotFoundException::new);

        clearDefault(userId);
        target.markDefault();
        return target;
    }

    /** 기존 기본 배송지를 해제한다. 변경은 더티 체킹으로 반영된다. */
    private void clearDefault(Long userId) {
        deliveryAddressRepository.findAllByUserIdAndDefaultAddressTrue(userId)
                .forEach(DeliveryAddress::unmarkDefault);
    }
}
