package com.uptrail.shared.web;

import java.util.Map;

/**
 * JSON error body of every {@code /api/**} endpoint.
 */
public record ApiError(int status, String code, String message, Map<String, String> fieldErrors,
        String correlationId) {
}
