package com.uptrail.audit.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uptrail.audit.domain.AggregateType;
import com.uptrail.audit.domain.AuditEvent;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findByAggregateTypeAndAggregateKeyOrderByCreatedAtDescIdDesc(AggregateType type, String key);

    @Query("""
            select e from AuditEvent e
            where (:type is null or e.aggregateType = :type)
              and (:key = '' or e.aggregateKey = :key)
              and (:eventType = '' or e.eventType = :eventType)
              and e.createdAt >= :from and e.createdAt < :to
            order by e.createdAt desc, e.id desc
            """)
    Page<AuditEvent> search(@Param("type") AggregateType type, @Param("key") String key,
            @Param("eventType") String eventType, @Param("from") Instant from, @Param("to") Instant to,
            Pageable pageable);
}
