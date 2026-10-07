package com.uptrail.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.uptrail.model.AggregateType;
import com.uptrail.model.AuditEvent;
import com.uptrail.repo.AuditEventRepo;
import com.uptrail.shared.time.BusinessClock;
import com.uptrail.shared.web.CorrelationId;

import tools.jackson.databind.json.JsonMapper;

/**
 * Appends business events inside the caller's transaction. Snapshots hold selected business fields only;
 * never passwords, tokens or file contents.
 */
@Service
public class AuditService {

    public record Change(AggregateType type, String key, String eventType, Long actorEmployeeId, String fromState,
            String toState, String reason, Map<String, ?> snapshot) {
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AuditEventRepo events;
    private final BusinessClock clock;

    public AuditService(AuditEventRepo events, BusinessClock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEvent record(Change change) {
        String snapshot = change.snapshot() == null || change.snapshot().isEmpty() ? null
                : JSON.writeValueAsString(stringValues(change.snapshot()));
        return events.save(AuditEvent.of(change.type(), change.key(), change.eventType(), change.actorEmployeeId(),
                change.fromState(), change.toState(), change.reason(), snapshot, CorrelationId.current(),
                clock.now()));
    }

    /** Values are stored as strings so snapshots stay readable and independent of Java types. */
    private static Map<String, Object> stringValues(Map<String, ?> snapshot) {
        Map<String, Object> result = new LinkedHashMap<>();
        snapshot.forEach((key, value) -> result.put(key, value == null ? null
                : value instanceof Map<?, ?> nested ? stringValues(castMap(nested)) : String.valueOf(value)));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> castMap(Map<?, ?> map) {
        return (Map<String, ?>) map;
    }
}
