package com.uptrail.model;

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

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An append-only business event. Ledger entries and email outbox rows point to the event that caused
 * them, which is what makes every balance change traceable.
 */
@Entity
@Immutable
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "aggregate_type", nullable = false, length = 32)
    private AggregateType aggregateType;

    @Column(name = "aggregate_key", nullable = false, length = 64)
    private String aggregateKey;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "actor_employee_id")
    private Long actorEmployeeId;

    @Column(name = "from_state", length = 24)
    private String fromState;

    @Column(name = "to_state", length = 24)
    private String toState;

    @Column(name = "reason", length = 2000)
    private String reason;

    @Column(name = "snapshot_json", columnDefinition = "json")
    private String snapshotJson;

    @Column(name = "correlation_id", nullable = false, length = 36)
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditEvent() {
    }

    public static AuditEvent of(AggregateType type, String aggregateKey, String eventType, Long actorEmployeeId,
            String fromState, String toState, String reason, String snapshotJson, String correlationId,
            Instant now) {
        AuditEvent event = new AuditEvent();
        event.aggregateType = Objects.requireNonNull(type);
        event.aggregateKey = Objects.requireNonNull(aggregateKey);
        event.eventType = Objects.requireNonNull(eventType);
        event.actorEmployeeId = actorEmployeeId;
        event.fromState = fromState;
        event.toState = toState;
        event.reason = reason;
        event.snapshotJson = snapshotJson;
        event.correlationId = Objects.requireNonNull(correlationId);
        event.createdAt = now;
        return event;
    }

    public Long getId() {
        return id;
    }

    public AggregateType getAggregateType() {
        return aggregateType;
    }

    public String getAggregateKey() {
        return aggregateKey;
    }

    public String getEventType() {
        return eventType;
    }

    public Long getActorEmployeeId() {
        return actorEmployeeId;
    }

    public String getFromState() {
        return fromState;
    }

    public String getToState() {
        return toState;
    }

    public String getReason() {
        return reason;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
