package com.kurly.oms.infrastructure.repository;

import com.kurly.oms.domain.returnorder.OmsReturnProcess;
import com.kurly.oms.domain.returnorder.OmsReturnProcessRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OmsReturnProcessJpaRepository
        extends JpaRepository<OmsReturnProcess, Long>, OmsReturnProcessRepository {
}
