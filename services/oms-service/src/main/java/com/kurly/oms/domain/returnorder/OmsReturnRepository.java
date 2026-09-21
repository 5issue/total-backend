package com.kurly.oms.domain.returnorder;

import java.util.Optional;

public interface OmsReturnRepository {
    OmsReturn save(OmsReturn omsReturn);

    boolean existsByOmsOrderId(Long omsOrderId);

    Optional<OmsReturn> findByOmsOrderIdWithDetails(Long omsOrderId);

    Optional<OmsReturn> findByIdWithDetails(Long returnId);
}