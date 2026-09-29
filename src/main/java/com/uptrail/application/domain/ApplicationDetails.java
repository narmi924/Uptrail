package com.uptrail.application.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.uptrail.catalogue.domain.CategoryCode;
import com.uptrail.entitlement.domain.Session;

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
