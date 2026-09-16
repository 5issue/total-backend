package com.kurly.oms.domain.returnorder;

import java.util.List;
import java.util.Optional;

public interface OmsReturnProcessRepository {
    OmsReturnProcess save(OmsReturnProcess process);
    Optional<OmsReturnProcess> findByReturnId(Long returnId);
    boolean existsBySourceEventId(String sourceEventId);
    List<OmsReturnProcess> findAll();
}
