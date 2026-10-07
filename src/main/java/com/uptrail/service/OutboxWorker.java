package com.uptrail.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls the outbox. Disabled in tests, which call {@link OutboxProcessor#processBatch()} directly.
 */
@Component
@ConditionalOnProperty(name = "uptrail.mail.worker.enabled", havingValue = "true")
public class OutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);

    private final OutboxProcessor processor;

    public OutboxWorker(OutboxProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(initialDelayString = "PT15S", fixedDelayString = "${uptrail.mail.worker.poll-interval}")
    public void poll() {
        try {
            // Keep draining while full batches come back, so a backlog does not wait for the next poll.
            int processed;
            do {
                processed = processor.processBatch();
            } while (processed >= processor.batchSize());
        } catch (RuntimeException e) {
            // The next poll tries again; one broken batch must not stop the schedule.
            log.error("Outbox polling failed", e);
        }
    }
}
