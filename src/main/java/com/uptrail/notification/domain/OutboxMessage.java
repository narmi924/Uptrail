package com.uptrail.notification.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A notification written in the same transaction as the business change and sent later by the outbox
 * worker. Delivery is at least once: a crash after a successful send can repeat an email, never a
 * business change.
 */
@Entity
@Table(name = "email_outbox")
public class OutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "recipient_employee_id", nullable = false)
    private Long recipientEmployeeId;

    @Column(name = "recipient_email", nullable = false, length = 254)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "template_code", nullable = false, length = 40)
    private MailTemplate template;

    @Column(name = "payload_json", nullable = false, columnDefinition = "json")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 16)
    private OutboxStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OutboxMessage() {
    }

    public static OutboxMessage pending(Long eventId, Long recipientEmployeeId, String recipientEmail,
            MailTemplate template, String payloadJson, Instant now) {
        OutboxMessage message = new OutboxMessage();
        message.eventId = Objects.requireNonNull(eventId);
        message.recipientEmployeeId = Objects.requireNonNull(recipientEmployeeId);
        message.recipientEmail = Objects.requireNonNull(recipientEmail);
        message.template = Objects.requireNonNull(template);
        message.payloadJson = Objects.requireNonNull(payloadJson);
        message.status = OutboxStatus.PENDING;
        message.attempts = 0;
        message.nextAttemptAt = now;
        message.createdAt = now;
        return message;
    }

    /** Takes a lease so that another worker skips this message while it is being sent. */
    public void lease(Instant now, Duration leaseDuration) {
        this.status = OutboxStatus.SENDING;
        this.attempts++;
        this.leaseUntil = now.plus(leaseDuration);
    }

    public void markSent(Instant now) {
        this.status = OutboxStatus.SENT;
        this.sentAt = now;
        this.leaseUntil = null;
        this.lastError = null;
    }

    public void markFailedAttempt(String error, Instant nextAttempt, boolean giveUp) {
        this.status = giveUp ? OutboxStatus.FAILED : OutboxStatus.PENDING;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
        this.nextAttemptAt = nextAttempt;
        this.leaseUntil = null;
    }

    /** Manual retry of a failed message by an administrator. */
    public void retry(Instant now) {
        if (status != OutboxStatus.FAILED) {
            throw new IllegalStateException("Only failed messages can be retried");
        }
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getEventId() {
        return eventId;
    }

    public Long getRecipientEmployeeId() {
        return recipientEmployeeId;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public MailTemplate getTemplate() {
        return template;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
