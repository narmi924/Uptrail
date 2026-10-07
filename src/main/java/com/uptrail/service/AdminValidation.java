package com.uptrail.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.error.ErrorCode;

/**
 * Collects field errors of an administration form and raises them together, so the administrator sees
 * every problem at once.
 */
final class AdminValidation {

    static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    static final Pattern STAFF_NO = Pattern.compile("^[A-Za-z0-9-]{1,30}$");
    static final Pattern USERNAME = Pattern.compile("^[a-z0-9._-]{3,80}$");
    static final int MIN_PASSWORD = 10;

    private final Map<String, String> errors = new LinkedHashMap<>();

    String required(String field, String value, int maxLength, String label) {
        String trimmed = value == null ? null : value.strip();
        if (trimmed == null || trimmed.isEmpty()) {
            errors.putIfAbsent(field, "Enter the " + label + ".");
            return null;
        }
        if (trimmed.length() > maxLength) {
            errors.putIfAbsent(field, "Use at most " + maxLength + " characters.");
        }
        return trimmed;
    }

    String optional(String field, String value, int maxLength) {
        String trimmed = value == null ? null : value.strip();
        if (trimmed == null || trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > maxLength) {
            errors.putIfAbsent(field, "Use at most " + maxLength + " characters.");
        }
        return trimmed;
    }

    void matches(String field, String value, Pattern pattern, String message) {
        if (value != null && !pattern.matcher(value).matches()) {
            errors.putIfAbsent(field, message);
        }
    }

    BigDecimal money(String field, BigDecimal value, boolean allowZero) {
        if (value == null) {
            errors.putIfAbsent(field, "Enter an amount.");
            return null;
        }
        if (value.signum() < 0 || (!allowZero && value.signum() == 0)) {
            errors.putIfAbsent(field, allowZero ? "The amount cannot be negative." : "The amount must be above zero.");
        } else if (value.stripTrailingZeros().scale() > 2) {
            errors.putIfAbsent(field, "Use at most two decimal places.");
        } else if (value.compareTo(new BigDecimal("999999.99")) > 0) {
            errors.putIfAbsent(field, "The amount is too large.");
        }
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    void reject(String field, String message) {
        errors.putIfAbsent(field, message);
    }

    boolean hasErrors() {
        return !errors.isEmpty();
    }

    void throwIfAny() {
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Some fields need attention.", errors);
        }
    }
}
