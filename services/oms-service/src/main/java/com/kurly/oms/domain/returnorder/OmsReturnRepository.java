package com.kurly.oms.domain.returnorder;

import java.util.Optional;

public interface OmsReturnRepository {
    void save(OmsReturn omsReturn);

    boolean existsByOmsOrderId(Long omsOrderId);

    Optional<OmsReturn> findByOmsOrderIdWithDetails(Long omsOrderId);

}