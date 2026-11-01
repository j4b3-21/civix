package com.civix.audit.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.civix.audit.domain.AuditCategory;
import com.civix.audit.domain.AuditEvent;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    Page<AuditEvent> findByUserId(UUID userId, Pageable pageable);

    Page<AuditEvent> findByUserIdAndCategory(UUID userId, AuditCategory category, Pageable pageable);
}
