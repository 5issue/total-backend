package com.kurly.payment.domain.repository;

import com.kurly.payment.domain.entity.PaymentCancel;

import java.util.List;
import java.util.Optional;

public interface PaymentCancelRepository {

    <S extends PaymentCancel> S save(S paymentCancel);

    Optional<PaymentCancel> findById(Long id);

    /** 한 결제의 취소 이력. 부분 취소 누적액을 계산할 때 쓴다. */
    List<PaymentCancel> findAllByPaymentId(Long paymentId);
}
