package com.uptrail.notification.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uptrail.notification.domain.OutboxMessage;
import com.uptrail.notification.domain.OutboxStatus;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {

    long countByStatus(OutboxStatus status);

    long countByRecipientEmployeeId(Long recipientEmployeeId);

    /**
     * Due messages, locked for the calling transaction. SKIP LOCKED lets several workers run side by side
     * without waiting for each other or taking the same message. A SENDING message whose lease expired (its
     * worker stopped) is due again.
     */
    @Query(value = """
            SELECT id FROM email_outbox
            WHERE (status = 'PENDING' AND next_attempt_at <= :now)
               OR (status = 'SENDING' AND lease_until < :now)
            ORDER BY next_attempt_at, id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Long> lockDue(@Param("now") Instant now, @Param("limit") int limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from OutboxMessage m where m.id = :id")
    Optional<OutboxMessage> lockById(@Param("id") Long id);

    Page<OutboxMessage> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    Page<OutboxMessage> findByStatusOrderByCreatedAtDescIdDesc(OutboxStatus status, Pageable pageable);
}
