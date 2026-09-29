package com.uptrail.shared.error;

/**
 * Business and protocol error codes shared by pages and the JSON API.
 */
public enum ErrorCode {

    MALFORMED_REQUEST(400),
    AUTHENTICATION_REQUIRED(401),
    FORBIDDEN(403),
    CSRF_INVALID(403),
    RESOURCE_NOT_FOUND(404),
    STALE_VERSION(409),
    ALREADY_PROCESSED(409),
    IDEMPOTENCY_KEY_REUSED(409),
    CONFLICT(409),
    VALIDATION_FAILED(422),
    INSUFFICIENT_BUDGET(422),
    INSUFFICIENT_DAYS(422),
    PERIOD_OVERLAP(422),
    INVALID_STATE(422),
    NON_WORKING_BOUNDARY(422),
    MISSING_ANNUAL_ACCOUNT(422),
    MISSING_APPROVER(422),
    COURSE_NOT_ENDED(422),
    HOLIDAY_CALENDAR_UNCONFIRMED(422),
    SCHEDULE_OUTDATED(422),
    YEAR_NOT_OPEN(422),
    SELF_APPROVAL(422),
    INVALID_DOCUMENT(422),
    CLAIM_NOT_ELIGIBLE(422),
    RULE_VIOLATION(422),
    INTERNAL_ERROR(500),
    TEMPORARY_CONTENTION(503);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
