package com.uptrail.claim.service;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.uptrail.claim.repository.ClaimDocumentRepository;

/**
 * Removes stored document files that no claim document row references, for example after a crash between
 * writing a file and committing its transaction. Files younger than one hour are left alone because their
 * transaction may still be running.
 */
@Component
@ConditionalOnProperty(name = "uptrail.documents.sweep.enabled", havingValue = "true", matchIfMissing = true)
public class DocumentSweepJob {

    static final Duration MINIMUM_AGE = Duration.ofHours(1);

    private static final Logger log = LoggerFactory.getLogger(DocumentSweepJob.class);

    private final ClaimDocumentRepository documents;
    private final DocumentStorage storage;

    public DocumentSweepJob(ClaimDocumentRepository documents, DocumentStorage storage) {
        this.documents = documents;
        this.storage = storage;
    }

    @Scheduled(initialDelayString = "${uptrail.documents.sweep.initial-delay:PT5M}",
            fixedDelayString = "${uptrail.documents.sweep.interval:PT6H}")
    public void sweep() {
        Set<String> known = new HashSet<>(documents.findAllStorageKeys());
        int removed = storage.sweepOrphans(known, MINIMUM_AGE);
        if (removed > 0) {
            log.info("Removed {} unreferenced document file(s)", removed);
        }
    }
}
