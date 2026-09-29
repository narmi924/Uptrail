package com.uptrail.claim.service;

import java.math.BigDecimal;
import java.util.Optional;

import com.uptrail.application.domain.ApplicationStatus;
import com.uptrail.application.domain.CourseApplication;

/**
 * Rules for fee claims: only a completed course in a fee-paying category with a fee can be claimed,
 * the fee must have been paid by the employee, and the amount is more than zero and at most the course fee
 * recorded on the application.
 */
public final class ClaimPolicy {

    private ClaimPolicy() {
    }

    /** Why the application cannot be claimed, or empty when it can (ignoring an existing claim). */
    public static Optional<String> ineligibility(CourseApplication application) {
        if (!application.getCategory().isFeePaying()) {
            return Optional.of("Internal training has no course fee to claim.");
        }
        if (application.getCourseFee() == null || application.getCourseFee().signum() <= 0) {
            return Optional.of("This course has no fee to claim.");
        }
        if (application.getStatus() != ApplicationStatus.COMPLETED) {
            return Optional.of("A fee can be claimed after you confirm attendance of the completed course.");
        }
        return Optional.empty();
    }

    /** A problem with the claimed amount, or empty when it is acceptable. */
    public static Optional<String> amountProblem(BigDecimal amount, BigDecimal courseFee) {
        if (amount == null) {
            return Optional.of("Enter the amount you paid.");
        }
        if (amount.signum() <= 0) {
            return Optional.of("The amount must be more than zero.");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            return Optional.of("Use at most two decimal places.");
        }
        if (amount.compareTo(courseFee) > 0) {
            return Optional.of("The amount cannot be more than the approved course fee of SGD "
                    + courseFee.setScale(2).toPlainString() + ".");
        }
        return Optional.empty();
    }
}
