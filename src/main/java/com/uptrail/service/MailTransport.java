package com.uptrail.service;

/**
 * Delivers one rendered email. Implementations throw on failure; the outbox processor records the error
 * and schedules a retry.
 */
public interface MailTransport {

    record OutgoingMail(Long outboxId, String to, String subject, String body) {
    }

    void send(OutgoingMail mail) throws Exception;

    String name();
}
