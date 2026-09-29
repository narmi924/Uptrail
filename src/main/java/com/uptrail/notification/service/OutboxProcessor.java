package com.uptrail.notification.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.uptrail.notification.domain.MailTemplate;
import com.uptrail.notification.domain.OutboxMessage;
import com.uptrail.notification.domain.OutboxStatus;
import com.uptrail.notification.repository.OutboxMessageRepository;
import com.uptrail.shared.time.BusinessClock;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sends due outbox messages. Each batch is leased in a short transaction (row locks with SKIP LOCKED), the
 * emails are sent outside any transaction, and each result is recorded in its own transaction. A failed
 * send is retried with exponential backoff until the attempt limit, then the message is FAILED and waits
 * for an administrator. Business transactions never wait for mail.
 */
@Service
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Duration MAX_BACKOFF = Duration.ofHours(1);

    private record Leased(Long id, int attempt, String to, MailTemplate template, String payloadJson) {
    }

    private final OutboxMessageRepository outbox;
    private final MailRenderer renderer;
    private final MailTransport transport;
    private final BusinessClock clock;
    private final TransactionTemplate tx;
    private final Duration lease;
    private final Duration backoff;
    private final int maxAttempts;
    private final int batchSize;

    public OutboxProcessor(OutboxMessageRepository outbox, MailRenderer renderer, MailTransport transport,
            BusinessClock clock, PlatformTransactionManager transactionManager,
            @Value("${uptrail.mail.worker.lease}") Duration lease,
            @Value("${uptrail.mail.worker.backoff:PT1M}") Duration backoff,
            @Value("${uptrail.mail.worker.max-attempts}") int maxAttempts,
            @Value("${uptrail.mail.worker.batch-size}") int batchSize) {
        this.outbox = outbox;
        this.renderer = renderer;
        this.transport = transport;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.lease = lease;
        this.backoff = backoff;
        this.maxAttempts = maxAttempts;
        this.batchSize = batchSize;
    }

    public int batchSize() {
        return batchSize;
    }

    /** Processes one batch of due messages and returns how many were attempted. */
    public int processBatch() {
        List<Leased> batch = leaseBatch();
        for (Leased message : batch) {
            String error = null;
            try {
                MailRenderer.RenderedMail mail = renderer.render(message.template(), values(message.payloadJson()));
                transport.send(new MailTransport.OutgoingMail(message.id(), message.to(), mail.subject(), mail.body()));
            } catch (Exception e) {
                error = e.getClass().getSimpleName() + ": " + e.getMessage();
                log.warn("Email {} ({}) attempt {} failed: {}", message.id(), message.template(), message.attempt(),
                        error);
            }
            record(message, error);
        }
        return batch.size();
    }

    private List<Leased> leaseBatch() {
        List<Leased> leased = tx.execute(status -> {
            Instant now = clock.now();
            List<Leased> result = new ArrayList<>();
            for (Long id : outbox.lockDue(now, batchSize)) {
                OutboxMessage message = outbox.findById(id).orElseThrow();
                message.lease(now, lease);
                result.add(new Leased(message.getId(), message.getAttempts(), message.getRecipientEmail(),
                        message.getTemplate(), message.getPayloadJson()));
            }
            return result;
        });
        return leased == null ? List.of() : leased;
    }

    private void record(Leased leased, String error) {
        tx.executeWithoutResult(status -> {
            OutboxMessage message = outbox.lockById(leased.id()).orElse(null);
            // Another worker re-leased the message after our lease expired: its result counts, not ours.
            if (message == null || message.getStatus() != OutboxStatus.SENDING
                    || message.getAttempts() != leased.attempt()) {
                return;
            }
            Instant now = clock.now();
            if (error == null) {
                message.markSent(now);
            } else {
                boolean giveUp = leased.attempt() >= maxAttempts;
                message.markFailedAttempt(error, now.plus(delayAfter(leased.attempt())), giveUp);
                if (giveUp) {
                    log.warn("Email {} ({}) failed {} times and needs an administrator", leased.id(),
                            leased.template(), leased.attempt());
                }
            }
        });
    }

    /** 1, 2, 4, 8 ... times the base delay, at most one hour. */
    Duration delayAfter(int attempt) {
        Duration delay = backoff.multipliedBy(1L << Math.min(Math.max(attempt - 1, 0), 20));
        return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
    }

    private static Map<String, String> values(String payloadJson) {
        return JSON.readValue(payloadJson, new TypeReference<Map<String, String>>() {
        });
    }
}
