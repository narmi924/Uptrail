package com.uptrail.audit.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uptrail.audit.domain.AuditEvent;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
}
