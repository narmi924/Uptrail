package com.uptrail.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uptrail.notification.domain.OutboxMessage;
import com.uptrail.notification.domain.OutboxStatus;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {

    long countByStatus(OutboxStatus status);
}
