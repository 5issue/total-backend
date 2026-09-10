package com.kurly.payment.application;

import com.kurly.payment.domain.entity.Payment;
import com.kurly.payment.domain.repository.PaymentRepository;
import com.kurly.payment.exception.PaymentNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 조회.
 *
 * <p>소유권 검사는 공통 인증 처리기가 아니라 이 계층의 책임이다. 조회에 {@code userId}를 조건으로
 * 함께 걸어 타인의 결제는 애초에 결과에 잡히지 않게 한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentQueryService {

    private final PaymentRepository paymentRepository;

    /**
     * 영수증 조회.
     *
     * <p>없는 결제와 타인의 결제를 같은 예외로 처리한다. 구분해 응답하면 ID를 훑어 남의 결제
     * 존재 여부를 알아낼 수 있다.
     */
    @Transactional(readOnly = true)
    public Payment getOwnedPayment(Long paymentId, Long userId) {
        return paymentRepository.findByIdAndUserId(paymentId, userId)
                .orElseThrow(PaymentNotFoundException::new);
    }
}
