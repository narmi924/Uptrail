package com.uptrail.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uptrail.model.AggregateType;
import com.uptrail.model.AuditEvent;
import com.uptrail.repo.AuditEventRepo;
import com.uptrail.model.User;
import com.uptrail.model.Role;
import com.uptrail.model.MailTemplate;
import com.uptrail.model.OutboxMessage;
import com.uptrail.model.OutboxStatus;
import com.uptrail.repo.OutboxMessageRepo;
import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.error.ErrorCode;
import com.uptrail.shared.error.NotFoundException;
import com.uptrail.shared.time.BusinessClock;
import com.uptrail.shared.tx.WriteTransaction;

/**
 * Operations workspace: read-only audit search, the email outbox with a manual retry of failed messages,
 * and the ledger consistency check. Retrying an email is the only change made here, and it is audited.
 */
@Service
@Transactional(readOnly = true)
public class OperationsService {

    public record AuditFilter(AggregateType type, String key, String eventType, LocalDate from, LocalDate to) {
    }

    public record AuditRow(Long id, Instant at, AggregateType type, String key, String eventType, String actorName,
            String fromState, String toState, String reason, String snapshotJson, String correlationId) {
    }

    public record OutboxRow(Long id, Instant createdAt, MailTemplate template, String recipientName,
            String recipientEmail, OutboxStatus status, int attempts, Instant nextAttemptAt, Instant sentAt,
            String lastError) {
    }

    private static final Logger log = LoggerFactory.getLogger(OperationsService.class);

    private final AuditEventRepo auditEvents;
    private final OutboxMessageRepo outbox;
    private final StaffService directory;
    private final LedgerReconciliationService reconciliation;
    private final AuditService audit;
    private final BusinessClock clock;

    public OperationsService(AuditEventRepo auditEvents, OutboxMessageRepo outbox,
            StaffService directory, LedgerReconciliationService reconciliation, AuditService audit,
            BusinessClock clock) {
        this.auditEvents = auditEvents;
        this.outbox = outbox;
        this.directory = directory;
        this.reconciliation = reconciliation;
        this.audit = audit;
        this.clock = clock;
    }

    public AuditFilter defaultFilter(AggregateType type, String key, String eventType, LocalDate from, LocalDate to) {
        LocalDate end = to == null ? clock.today() : to;
        LocalDate start = from == null ? end.minusDays(30) : from;
        return new AuditFilter(type, key == null ? "" : key.strip(),
                eventType == null ? "" : eventType.strip().toUpperCase(java.util.Locale.ROOT), start, end);
    }

    public Page<AuditRow> audit(AuditFilter filter, Pageable pageable) {
        if (filter.to().isBefore(filter.from())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Choose a period whose end is on or after its start.");
        }
        Page<AuditEvent> page = auditEvents.search(filter.type(), filter.key(), filter.eventType(),
                clock.startOf(filter.from()), clock.startOf(filter.to().plusDays(1)), pageable);
        Set<Long> actors = new HashSet<>();
        page.forEach(e -> {
            if (e.getActorEmployeeId() != null) {
                actors.add(e.getActorEmployeeId());
            }
        });
        Map<Long, String> names = directory.namesOf(actors);
        return page.map(e -> new AuditRow(e.getId(), e.getCreatedAt(), e.getAggregateType(), e.getAggregateKey(),
                e.getEventType(), e.getActorEmployeeId() == null ? "System"
                        : names.getOrDefault(e.getActorEmployeeId(), "Unknown"),
                e.getFromState(), e.getToState(), e.getReason(), e.getSnapshotJson(), e.getCorrelationId()));
    }

    public Page<OutboxRow> outbox(OutboxStatus status, Pageable pageable) {
        Page<OutboxMessage> page = status == null ? outbox.findAllByOrderByCreatedAtDescIdDesc(pageable)
                : outbox.findByStatusOrderByCreatedAtDescIdDesc(status, pageable);
        Map<Long, String> names = directory.namesOf(page.getContent().stream()
                .map(OutboxMessage::getRecipientEmployeeId).toList());
        return page.map(m -> new OutboxRow(m.getId(), m.getCreatedAt(), m.getTemplate(),
                names.get(m.getRecipientEmployeeId()), m.getRecipientEmail(), m.getStatus(), m.getAttempts(),
                m.getNextAttemptAt(), m.getSentAt(), m.getLastError()));
    }

    /** Message counts keyed by status name. */
    public Map<String, Long> outboxCounts() {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (OutboxStatus status : OutboxStatus.values()) {
            counts.put(status.name(), outbox.countByStatus(status));
        }
        return counts;
    }

    /** Puts a FAILED email back into the queue with a fresh attempt count. */
    @WriteTransaction
    public void retry(User admin, Long outboxId) {
        if (!admin.hasRole(Role.ADMIN)) {
            throw new NotFoundException();
        }
        OutboxMessage message = outbox.lockById(outboxId).orElseThrow(NotFoundException::new);
        if (message.getStatus() != OutboxStatus.FAILED) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Only failed emails can be retried; this one is "
                    + message.getStatus().name().toLowerCase(java.util.Locale.ROOT) + ".");
        }
        String previousError = message.getLastError();
        message.retry(clock.now());
        audit.record(new AuditService.Change(AggregateType.OUTBOX, message.getId().toString(), "RETRY_REQUESTED",
                admin.getUserId(), OutboxStatus.FAILED.name(), OutboxStatus.PENDING.name(),
                "Email queued again by an administrator",
                Map.of("template", message.getTemplate().name(), "lastError",
                        Objects.requireNonNullElse(previousError, ""))));
        log.info("Email {} queued again by administrator {}", outboxId, admin.getUserId());
    }

    public record LedgerView(LedgerReconciliationService.Result result, Map<Long, String> names, Instant checkedAt) {
    }

    /** Recomputes every balance from the applications and claims and compares it with the ledger sums. */
    public LedgerView ledgerCheck() {
        LedgerReconciliationService.Result result = reconciliation.check();
        Map<Long, String> names = directory.namesOf(result.mismatches().stream()
                .map(LedgerReconciliationService.Mismatch::employeeId).toList());
        return new LedgerView(result, names, clock.now());
    }

    public List<AggregateType> aggregateTypes() {
        return List.of(AggregateType.values());
    }
}
