package com.uptrail.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.uptrail.model.ApplicationStatus;
import com.uptrail.model.CategoryCode;
import com.uptrail.model.DaySession;
import com.uptrail.model.LedgerEntryType;
import com.uptrail.model.Session;

/**
 * Read models of course applications for pages, CSV exports and emails. Entities never leave the service
 * layer; templates only see these records.
 */
public final class ApplicationViews {

    private ApplicationViews() {
    }

    public record Row(Long id, String referenceNo, Long employeeId, String employeeName, String courseTitle,
            CategoryCode category, String categoryName, String providerName, LocalDate startDate,
            Session startSession, LocalDate endDate, Session endSession, int units, BigDecimal fee,
            ApplicationStatus status, Instant updatedAt) {
    }

    public record DayLine(LocalDate date, DaySession session, int units) {
    }

    public record ExcludedLine(LocalDate date, String reason) {
    }

    public record TimelineEntry(Instant at, String actorName, String label, String fromState, String toState,
            String reason) {
    }

    public record LedgerLine(Instant at, int year, LedgerEntryType type, int reservedUnits, int committedUnits,
            BigDecimal reservedAmount, BigDecimal committedAmount, BigDecimal reimbursedAmount) {
    }

    public record Actions(boolean canEdit, boolean canDelete, boolean canCancel, boolean canComplete,
            String completeNote, boolean canClaim, Long claimId) {

        public boolean any() {
            return canEdit || canDelete || canCancel || canComplete || canClaim || claimId != null;
        }
    }

    public record Detail(Long id, String referenceNo, Long employeeId, String employeeName, String department,
            String courseTitle, CategoryCode category, String categoryName, Long catalogueId, String providerName,
            LocalDate startDate, Session startSession, LocalDate endDate, Session endSession, BigDecimal fee,
            String justification, String workDissemination, ApplicationStatus status, Instant submittedAt,
            Instant updatedAt, long version, Long approverId, String approverName, String reviewerName,
            Instant reviewedAt, String decisionReason, String completionComment, Instant completedAt,
            String cancelReason, int totalUnits, List<DayLine> days, List<ExcludedLine> excluded,
            List<TimelineEntry> timeline, List<LedgerLine> ledger, Actions actions) {

        public boolean feePaying() {
            return category.isFeePaying();
        }
    }
}
