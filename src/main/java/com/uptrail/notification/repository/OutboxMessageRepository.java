package com.uptrail.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uptrail.notification.domain.OutboxMessage;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {
}
