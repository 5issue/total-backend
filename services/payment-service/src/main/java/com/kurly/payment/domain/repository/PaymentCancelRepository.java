package com.kurly.payment.domain.repository;

import com.kurly.payment.domain.entity.PaymentCancel;

import java.util.Optional;

public interface PaymentCancelRepository {

    <S extends PaymentCancel> S save(S paymentCancel);

    Optional<PaymentCancel> findById(Long id);
}
