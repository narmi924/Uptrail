package com.uptrail.application.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.uptrail.catalogue.domain.CategoryCode;
import com.uptrail.entitlement.domain.Session;
import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.error.ErrorCode;

/**
 * One course application of one employee. The course details are a snapshot taken at submission or at
 * the last update; state changes happen only through the lifecycle methods below.
 */
@Entity
@Table(name = "course_application")
public class CourseApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_no", nullable = false, length = 30)
    private String referenceNo;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "approver_id", nullable = false)
    private Long approverId;

    @Column(name = "catalogue_id")
    private Long catalogueId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "category_code", nullable = false, length = 20)
    private CategoryCode category;

    @Column(name = "course_title", nullable = false, length = 200)
    private String courseTitle;

    @Column(name = "provider_name", nullable = false, length = 160)
    private String providerName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "start_session", nullable = false, length = 2)
    private Session startSession;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "end_session", nullable = false, length = 2)
    private Session endSession;

    @Column(name = "course_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal courseFee;

    @Column(name = "justification", nullable = false, length = 2000)
    private String justification;

    @Column(name = "work_dissemination", length = 2000)
    private String workDissemination;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private ApplicationStatus status;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_comment", length = 2000)
    private String reviewComment;

    @Column(name = "completion_comment", length = 2000)
    private String completionComment;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancel_reason", length = 2000)
    private String cancelReason;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "client_request_id", nullable = false, length = 36)
    private String clientRequestId;

    @Column(name = "create_request_hash", nullable = false, length = 64)
    private String createRequestHash;

    @ElementCollection(fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @CollectionTable(name = "application_day", joinColumns = @JoinColumn(name = "application_id"))
    @OrderBy("trainingDate")
    private List<ScheduledDay> days = new ArrayList<>();

    protected CourseApplication() {
    }

    public static CourseApplication submit(Long employeeId, Long approverId, ApplicationDetails details,
            List<ScheduledDay> days, String clientRequestId, String requestHash, Instant now) {
        CourseApplication application = new CourseApplication();
        application.employeeId = Objects.requireNonNull(employeeId);
        application.approverId = Objects.requireNonNull(approverId);
        application.clientRequestId = Objects.requireNonNull(clientRequestId);
        application.createRequestHash = Objects.requireNonNull(requestHash);
        // Unique placeholder until the database id is known; replaced in the same transaction.
        application.referenceNo = "TMP" + clientRequestId.replace("-", "").substring(0, 27);
        application.applyDetails(details, days);
        application.status = ApplicationStatus.APPLIED;
        application.submittedAt = now;
        application.updatedAt = now;
        return application;
    }

    /** Assigns the human-readable reference once the id exists, e.g. {@code UPT-2026-000042}. */
    public void assignReferenceNumber(int year) {
        if (id == null) {
            throw new IllegalStateException("The application must be saved before it gets a reference number");
        }
        this.referenceNo = String.format(Locale.ROOT, "UPT-%d-%06d", year, id);
    }

    public void revise(ApplicationDetails details, List<ScheduledDay> newDays, Instant now) {
        transitionTo(ApplicationStatus.UPDATED, "updated");
        applyDetails(details, newDays);
        this.updatedAt = now;
    }

    public void approve(Long reviewerId, String comment, Instant now) {
        transitionTo(ApplicationStatus.APPROVED, "approved");
        recordDecision(reviewerId, comment, now);
    }

    public void reject(Long reviewerId, String comment, Instant now) {
        transitionTo(ApplicationStatus.REJECTED, "rejected");
        recordDecision(reviewerId, comment, now);
    }

    public void delete(Instant now) {
        transitionTo(ApplicationStatus.DELETED, "deleted");
        this.updatedAt = now;
    }

    public void cancel(String reason, Instant now) {
        transitionTo(ApplicationStatus.CANCELLED, "cancelled");
        this.cancelReason = requireText(reason);
        this.updatedAt = now;
    }

    public void complete(String experienceComments, Instant now) {
        transitionTo(ApplicationStatus.COMPLETED, "marked as completed");
        this.completionComment = requireText(experienceComments);
        this.completedAt = now;
        this.updatedAt = now;
    }

    /** Moves a pending application to another approver, for example after a routing change. */
    public void reassignApprover(Long newApproverId, Instant now) {
        if (!status.isPending()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Only applications waiting for a decision can be reassigned.");
        }
        this.approverId = Objects.requireNonNull(newApproverId);
        this.updatedAt = now;
    }

    private void recordDecision(Long reviewerId, String comment, Instant now) {
        this.reviewedBy = Objects.requireNonNull(reviewerId);
        this.reviewComment = requireText(comment);
        this.reviewedAt = now;
        this.updatedAt = now;
    }

    private void transitionTo(ApplicationStatus target, String verb) {
        if (!status.canTransitionTo(target)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Application " + referenceNo + " is "
                    + status.label().toLowerCase(Locale.ROOT) + " and cannot be " + verb + ".");
        }
        this.status = target;
    }

    private void applyDetails(ApplicationDetails details, List<ScheduledDay> newDays) {
        this.category = Objects.requireNonNull(details.category());
        this.catalogueId = details.catalogueId();
        this.courseTitle = requireText(details.courseTitle());
        this.providerName = requireText(details.providerName());
        this.startDate = Objects.requireNonNull(details.startDate());
        this.endDate = Objects.requireNonNull(details.endDate());
        this.startSession = Objects.requireNonNull(details.startSession());
        this.endSession = Objects.requireNonNull(details.endSession());
        this.courseFee = Objects.requireNonNull(details.courseFee());
        this.justification = requireText(details.justification());
        this.workDissemination = details.workDissemination();
        if (newDays == null || newDays.isEmpty()) {
            throw new IllegalArgumentException("An application needs at least one training day");
        }
        this.days.clear();
        this.days.addAll(newDays);
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A required text value is missing");
        }
        return value.trim();
    }

    public ApplicationDetails details() {
        return new ApplicationDetails(category, catalogueId, courseTitle, providerName, startDate, endDate,
                startSession, endSession, courseFee, justification, workDissemination);
    }

    public int totalUnits() {
        return days.stream().mapToInt(ScheduledDay::getUnits).sum();
    }

    /** Training units per calendar year of the actual training dates. */
    public Map<Integer, Integer> unitsByYear() {
        Map<Integer, Integer> result = new TreeMap<>();
        for (ScheduledDay day : days) {
            result.merge(day.getTrainingDate().getYear(), day.getUnits(), Integer::sum);
        }
        return result;
    }

    public Long getId() {
        return id;
    }

    public String getReferenceNo() {
        return referenceNo;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public Long getApproverId() {
        return approverId;
    }

    public Long getCatalogueId() {
        return catalogueId;
    }

    public CategoryCode getCategory() {
        return category;
    }

    public String getCourseTitle() {
        return courseTitle;
    }

    public String getProviderName() {
        return providerName;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Session getStartSession() {
        return startSession;
    }

    public Session getEndSession() {
        return endSession;
    }

    public BigDecimal getCourseFee() {
        return courseFee;
    }

    public String getJustification() {
        return justification;
    }

    public String getWorkDissemination() {
        return workDissemination;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public String getCompletionComment() {
        return completionComment;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public long getVersion() {
        return version;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public String getCreateRequestHash() {
        return createRequestHash;
    }

    public List<ScheduledDay> getDays() {
        return Collections.unmodifiableList(days);
    }
}
