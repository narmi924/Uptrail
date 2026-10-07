package com.uptrail.model;

import java.math.BigDecimal;
import java.time.LocalDate;


/**
 * The course details an employee enters. Values are validated by the application evaluator before they
 * are stored as the application's snapshot.
 */
public record ApplicationDetails(
        CategoryCode category,
        Long catalogueId,
        String courseTitle,
        String providerName,
        LocalDate startDate,
        LocalDate endDate,
        Session startSession,
        Session endSession,
        BigDecimal courseFee,
        String justification,
        String workDissemination) {
}
