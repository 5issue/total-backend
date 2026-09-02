package com.kurly.order.infrastructure.repository;

import com.kurly.order.domain.claim.RefundAttachment;
import com.kurly.order.domain.claim.RefundAttachmentRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundAttachmentJpaRepository extends RefundAttachmentRepository, JpaRepository<RefundAttachment, Long> {
}
