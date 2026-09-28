package com.uptrail.shared.web;

import java.util.UUID;

import org.slf4j.MDC;

/**
 * Request correlation id, kept in the logging MDC so audit events, logs and API errors share it.
 */
public final class CorrelationId {

    public static final String MDC_KEY = "correlationId";
    public static final String HEADER = "X-Correlation-Id";

    private CorrelationId() {
    }

    /** Returns the id of the current request, or a fresh id for work outside a request. */
    public static String current() {
        String value = MDC.get(MDC_KEY);
        return value != null ? value : UUID.randomUUID().toString();
    }
}
