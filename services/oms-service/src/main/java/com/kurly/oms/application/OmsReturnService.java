package com.kurly.oms.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OmsReturnService {

    @Transactional(readOnly = true)
    public Object listReturns() {
        return null;
    }

    @Transactional(readOnly = true)
    public Object getReturnDetail(Long returnId) {
        return null;
    }

    @Transactional
    public void approveColdChainReturn(Long returnId) {
    }

    @Transactional
    public void processLogisticsReturn(Long returnId) {
    }

    @Transactional
    public void receiveInspectionResult(Long returnId) {
    }
}
