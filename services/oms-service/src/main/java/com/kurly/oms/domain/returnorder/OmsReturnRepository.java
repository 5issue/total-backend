package com.kurly.oms.domain.returnorder;

import java.util.Optional;

public interface OmsReturnRepository {
    OmsReturn save(OmsReturn omsReturn);

    boolean existsByOmsOrderId(Long omsOrderId);

    boolean existsBySourceEventId(String sourceEventId);

    OmsReturn saveAndFlush(OmsReturn omsReturn);

    Optional<OmsReturn> findByOmsOrderIdWithDetails(Long omsOrderId);

    Optional<OmsReturn> findByIdWithDetails(Long returnId);
}