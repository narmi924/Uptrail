package com.uptrail.claim.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.error.ErrorCode;

/**
 * The single fee claim of a completed, fee-paying application. A rejected claim is revised and
 * resubmitted as the same claim with the next revision number.
 */
@Entity
@Table(name = "course_claim")
public class CourseClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "revision", nullable = false)
    private int revision;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_by_employee", nullable = false)
    private boolean paidByEmployee;

    @Column(name = "approver_id", nullable = false)
    private Long approverId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private ClaimStatus status;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "review_comment", length = 2000)
    private String reviewComment;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reimbursed_by")
    private Long reimbursedBy;

    @Column(name = "reimbursed_at")
    private Instant reimbursedAt;

    @Column(name = "reimbursement_reference", length = 80)
    private String reimbursementReference;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected CourseClaim() {
    }

    public static CourseClaim submit(Long applicationId, Long approverId, BigDecimal amount, Instant now) {
        CourseClaim claim = new CourseClaim();
        claim.applicationId = Objects.requireNonNull(applicationId);
        claim.approverId = Objects.requireNonNull(approverId);
        claim.revision = 1;
        claim.amount = Objects.requireNonNull(amount);
        claim.paidByEmployee = true;
        claim.status = ClaimStatus.SUBMITTED;
        claim.submittedAt = now;
        return claim;
    }

    public void resubmit(Long approverId, BigDecimal newAmount, Instant now) {
        requireStatus(ClaimStatus.REJECTED, "resubmitted");
        this.revision++;
        this.approverId = Objects.requireNonNull(approverId);
        this.amount = Objects.requireNonNull(newAmount);
        this.status = ClaimStatus.SUBMITTED;
        this.submittedAt = now;
        this.reviewedBy = null;
        this.reviewComment = null;
        this.reviewedAt = null;
    }

    public void approve(Long reviewerId, String comment, Instant now) {
        requireStatus(ClaimStatus.SUBMITTED, "approved");
        decide(ClaimStatus.APPROVED, reviewerId, comment, now);
    }

    public void reject(Long reviewerId, String comment, Instant now) {
        requireStatus(ClaimStatus.SUBMITTED, "rejected");
        decide(ClaimStatus.REJECTED, reviewerId, comment, now);
    }

    public void registerReimbursement(Long adminEmployeeId, String reference, Instant now) {
        requireStatus(ClaimStatus.APPROVED, "registered as reimbursed");
        this.status = ClaimStatus.REIMBURSED;
        this.reimbursedBy = Objects.requireNonNull(adminEmployeeId);
        this.reimbursementReference = Objects.requireNonNull(reference);
        this.reimbursedAt = now;
    }

    public void reassignApprover(Long newApproverId) {
        requireStatus(ClaimStatus.SUBMITTED, "reassigned");
        this.approverId = Objects.requireNonNull(newApproverId);
    }

    private void decide(ClaimStatus target, Long reviewerId, String comment, Instant now) {
        if (comment == null || comment.isBlank()) {
            throw new IllegalArgumentException("A decision reason is required");
        }
        this.status = target;
        this.reviewedBy = Objects.requireNonNull(reviewerId);
        this.reviewComment = comment.trim();
        this.reviewedAt = now;
    }

    private void requireStatus(ClaimStatus expected, String verb) {
        if (status != expected) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This claim is "
                    + status.label().toLowerCase(Locale.ROOT) + " and cannot be " + verb + ".");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public int getRevision() {
        return revision;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public boolean isPaidByEmployee() {
        return paidByEmployee;
    }

    public Long getApproverId() {
        return approverId;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Long getReviewedBy() {
        return reviewedBy;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Long getReimbursedBy() {
        return reimbursedBy;
    }

    public Instant getReimbursedAt() {
        return reimbursedAt;
    }

    public String getReimbursementReference() {
        return reimbursementReference;
    }

    public long getVersion() {
        return version;
    }
}
