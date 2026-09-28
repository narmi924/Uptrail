package com.uptrail.application.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Course application states, named exactly as in the course brief. DELETED, REJECTED, CANCELLED and
 * COMPLETED are terminal; terminal records are kept as history rather than removed.
 */
public enum ApplicationStatus {

    APPLIED("Applied"),
    UPDATED("Updated"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    DELETED("Deleted"),
    CANCELLED("Cancelled"),
    COMPLETED("Completed");

    /** States that block overlapping applications of the same employee. */
    public static final Set<ApplicationStatus> OVERLAP_BLOCKING = EnumSet.of(APPLIED, UPDATED, APPROVED);

    /** States waiting for a manager decision. */
    public static final Set<ApplicationStatus> PENDING = EnumSet.of(APPLIED, UPDATED);

    private final String label;

    ApplicationStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isPending() {
        return PENDING.contains(this);
    }

    public boolean isTerminal() {
        return this == REJECTED || this == DELETED || this == CANCELLED || this == COMPLETED;
    }

    public boolean canTransitionTo(ApplicationStatus target) {
        return switch (this) {
            case APPLIED, UPDATED -> target == UPDATED || target == DELETED || target == APPROVED
                    || target == REJECTED;
            case APPROVED -> target == CANCELLED || target == COMPLETED;
            case REJECTED, DELETED, CANCELLED, COMPLETED -> false;
        };
    }
}
