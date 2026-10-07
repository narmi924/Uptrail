package com.uptrail.service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.uptrail.model.ApplicationDetails;
import com.uptrail.model.ApplicationStatus;
import com.uptrail.model.CourseApplication;
import com.uptrail.model.ScheduledDay;
import com.uptrail.repo.CourseApplicationRepo;
import com.uptrail.model.AggregateType;
import com.uptrail.model.AuditEvent;
import com.uptrail.model.LedgerDelta;
import com.uptrail.model.LedgerEntryType;
import com.uptrail.model.TrainingEntitlement;
import com.uptrail.model.User;
import com.uptrail.model.Role;
import com.uptrail.model.MailTemplate;
import com.uptrail.repo.UserRepo;
import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.error.ErrorCode;
import com.uptrail.shared.error.NotFoundException;
import com.uptrail.shared.error.StaleVersionException;
import com.uptrail.shared.time.BusinessClock;
import com.uptrail.shared.tx.WriteTransaction;

/**
 * Lifecycle commands of course applications. Every command follows the write protocol: lock the applicant,
 * then the involved annual accounts in ascending year, then the application; re-validate inside the locks;
 * write the application, one audit event, net ledger changes and outbox messages in one transaction.
 */
@Service
public class CourseApplicationService {

    private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-fA-F-]{36}");
    private static final int MAX_TEXT = 2000;
    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    public enum Decision {
        APPROVE,
        REJECT
    }

    public record SubmitResult(Long applicationId, String referenceNo, boolean replayed) {
    }

    private final CourseApplicationRepo applications;
    private final UserRepo employees;
    private final ApplicationEvaluator evaluator;
    private final EntitlementService entitlements;
    private final AuditService audit;
    private final NotificationService notifications;
    private final AccessScopePolicy scope;
    private final BusinessClock clock;
    private final String baseUrl;

    public CourseApplicationService(CourseApplicationRepo applications, UserRepo employees,
            ApplicationEvaluator evaluator, EntitlementService entitlements, AuditService audit,
            NotificationService notifications, AccessScopePolicy scope, BusinessClock clock,
            @Value("${uptrail.base-url}") String baseUrl) {
        this.applications = applications;
        this.employees = employees;
        this.evaluator = evaluator;
        this.entitlements = entitlements;
        this.audit = audit;
        this.notifications = notifications;
        this.scope = scope;
        this.clock = clock;
        this.baseUrl = baseUrl;
    }

    // ------------------------------------------------------------------ submit

    @WriteTransaction
    public SubmitResult submit(User actor, ApplicationDetails details, String clientRequestId) {
        requireApplicant(actor);
        if (clientRequestId == null || !UUID_PATTERN.matcher(clientRequestId).matches()) {
            throw new BusinessException(ErrorCode.MALFORMED_REQUEST, "The form is missing its request id. Reload the "
                    + "page and submit again.");
        }
        User applicant = lockApplicant(actor);

        Optional<CourseApplication> previous = applications.findByApplicantIdAndClientRequestId(applicant.getUserId(),
                clientRequestId);
        if (previous.isPresent()) {
            ApplicationDetails normalised = evaluator.evaluate(applicant.getUserId(), details, previous.get().getId())
                    .details();
            if (previous.get().getCreateRequestHash().equals(RequestFingerprint.of(normalised))) {
                return new SubmitResult(previous.get().getId(), previous.get().getReferenceNo(), true);
            }
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED, "This form was already submitted with "
                    + "different details. Open a new application form to apply again.");
        }

        // The first pass finds the annual accounts involved; they are locked before the decisive check.
        ApplicationEvaluator.Evaluation draft = evaluator.evaluate(applicant.getUserId(), details, null);
        if (!draft.eligible()) {
            throw draft.toException();
        }
        Map<Integer, TrainingEntitlement> accounts = entitlements.lockAccounts(applicant.getUserId(), yearsOf(draft));
        ApplicationEvaluator.Evaluation evaluation = evaluator.evaluate(applicant.getUserId(), details, null);
        if (!evaluation.eligible()) {
            throw evaluation.toException();
        }

        ApplicationDetails normalised = evaluation.details();
        CourseApplication application = CourseApplication.submit(applicant.getUserId(), evaluation.approverId(),
                normalised, evaluation.scheduledDays(), clientRequestId, RequestFingerprint.of(normalised), clock.now());
        applications.saveAndFlush(application);
        application.assignReferenceNumber(clock.currentYear());

        AuditEvent event = record(application, "SUBMITTED", actor, null, null, snapshot(application));
        for (TrainingEntitlement account : accounts.values()) {
            entitlements.post(event.getId(), account, application.getId(), null, LedgerEntryType.RESERVE,
                    reservationOn(application, account.getCalendarYear()));
        }
        notifications.enqueue(event.getId(), application.getApproverId(), MailTemplate.APPLICATION_SUBMITTED,
                mailPayload(application, applicant.getName(), null, "/manager/applications/" + application.getId()));
        return new SubmitResult(application.getId(), application.getReferenceNo(), false);
    }

    // ------------------------------------------------------------------ update

    /** Replaces the details of a pending application; the old reservation is released and the new one taken. */
    @WriteTransaction
    public void update(User actor, Long applicationId, ApplicationDetails details, Long expectedVersion) {
        requireApplicant(actor);
        User applicant = lockApplicant(actor);
        CourseApplication current = ownApplication(actor, applicationId);
        requirePending(current, "updated");

        ApplicationEvaluator.Evaluation draft = evaluator.evaluate(applicant.getUserId(), details, applicationId);
        if (!draft.eligible()) {
            throw draft.toException();
        }
        Set<Integer> years = yearsOf(current);
        years.addAll(yearsOf(draft));
        Map<Integer, TrainingEntitlement> accounts = entitlements.lockAccounts(applicant.getUserId(), years);
        CourseApplication application = lockApplication(applicationId, expectedVersion);
        requirePending(application, "updated");
        ApplicationEvaluator.Evaluation evaluation = evaluator.evaluate(applicant.getUserId(), details, applicationId);
        if (!evaluation.eligible()) {
            throw evaluation.toException();
        }

        Map<Integer, LedgerDelta> held = new LinkedHashMap<>();
        accounts.values().forEach(a -> held.put(a.getCalendarYear(),
                entitlements.contribution(a.getId(), application.getId())));
        Map<String, Object> before = snapshot(application);
        String fromState = application.getStatus().name();
        application.revise(evaluation.details(), evaluation.scheduledDays(), clock.now());
        applications.flush();

        Map<String, Object> change = new LinkedHashMap<>();
        change.put("before", before);
        change.put("after", snapshot(application));
        AuditEvent event = record(application, "UPDATED", actor, fromState, null, change);
        for (TrainingEntitlement account : accounts.values()) {
            LedgerDelta old = held.get(account.getCalendarYear());
            LedgerDelta target = reservationOn(application, account.getCalendarYear());
            LedgerDelta delta = new LedgerDelta(target.reservedUnits() - old.reservedUnits(), 0,
                    target.reservedAmount().subtract(old.reservedAmount()), BigDecimal.ZERO, BigDecimal.ZERO);
            entitlements.post(event.getId(), account, application.getId(), null, LedgerEntryType.UPDATE_RESERVATION,
                    delta);
        }
        notifications.enqueue(event.getId(), application.getApproverId(), MailTemplate.APPLICATION_UPDATED,
                mailPayload(application, applicant.getName(), null, "/manager/applications/" + application.getId()));
    }

    // ------------------------------------------------------------------ delete, cancel, complete

    /** Withdraws a pending application. It stays in the history as DELETED and its reservation is released. */
    @WriteTransaction
    public void delete(User actor, Long applicationId, Long expectedVersion) {
        requireApplicant(actor);
        User applicant = lockApplicant(actor);
        CourseApplication current = ownApplication(actor, applicationId);
        requirePending(current, "deleted");
        Map<Integer, TrainingEntitlement> accounts = entitlements.lockAccounts(applicant.getUserId(), yearsOf(current));
        CourseApplication application = lockApplication(applicationId, expectedVersion);
        String fromState = application.getStatus().name();
        application.delete(clock.now());
        applications.flush();
        AuditEvent event = record(application, "DELETED", actor, fromState, null, Map.of());
        releaseAll(event, application, accounts);
    }

    /** Cancels an approved course that has not been completed; the approved days and budget are released. */
    @WriteTransaction
    public void cancel(User actor, Long applicationId, Long expectedVersion, String reason) {
        requireApplicant(actor);
        String text = requiredText(reason, "reason", "Give a reason for cancelling the course.");
        User applicant = lockApplicant(actor);
        CourseApplication current = ownApplication(actor, applicationId);
        requireStatus(current, ApplicationStatus.APPROVED, "cancelled");
        Map<Integer, TrainingEntitlement> accounts = entitlements.lockAccounts(applicant.getUserId(), yearsOf(current));
        CourseApplication application = lockApplication(applicationId, expectedVersion);
        requireStatus(application, ApplicationStatus.APPROVED, "cancelled");
        application.cancel(text, clock.now());
        applications.flush();
        AuditEvent event = record(application, "CANCELLED", actor, ApplicationStatus.APPROVED.name(), text, Map.of());
        releaseAll(event, application, accounts);
    }

    /**
     * Confirms attendance after the course has ended. The approved commitment stays in the ledger: the days
     * and fee were already counted at approval and are not charged again.
     */
    @WriteTransaction
    public void complete(User actor, Long applicationId, Long expectedVersion, String experienceComments) {
        requireApplicant(actor);
        String text = requiredText(experienceComments, "experienceComments",
                "Describe your experience of the course before marking it as completed.");
        lockApplicant(actor);
        CourseApplication current = ownApplication(actor, applicationId);
        requireStatus(current, ApplicationStatus.APPROVED, "marked as completed");
        CourseApplication application = lockApplication(applicationId, expectedVersion);
        requireStatus(application, ApplicationStatus.APPROVED, "marked as completed");
        if (!clock.today().isAfter(application.getEndDate())) {
            throw new BusinessException(ErrorCode.COURSE_NOT_ENDED, "You can confirm attendance after the course ends "
                    + "on " + DAY.format(application.getEndDate()) + ".");
        }
        application.complete(text, clock.now());
        applications.flush();
        record(application, "COMPLETED", actor, ApplicationStatus.APPROVED.name(), text, Map.of());
    }

    // ------------------------------------------------------------------ manager decision

    /**
     * Approves or rejects a pending application assigned to the acting manager. A reason is mandatory for
     * both decisions. Approval converts the reservation into a commitment; rejection releases it.
     */
    @WriteTransaction
    public void decide(User manager, Long applicationId, Decision decision, String reason, Long expectedVersion) {
        if (!manager.hasRole(Role.MANAGER)) {
            throw new NotFoundException();
        }
        String text = requiredText(reason, "reason", decision == Decision.APPROVE
                ? "Give the reason for approving, including any conditions."
                : "Give the reason for rejecting, so the applicant knows what to change.");
        // Only the immutable owner id is read before locking; the application itself is loaded after the
        // applicant's row lock, which every writer of this application holds.
        Long applicantId = applications.findEmployeeIdById(applicationId).orElseThrow(NotFoundException::new);
        User applicant = employees.lockById(applicantId).orElseThrow(NotFoundException::new);
        CourseApplication current = applications.findById(applicationId).orElseThrow(NotFoundException::new);
        requireAssignedApprover(manager, current);
        Map<Integer, TrainingEntitlement> accounts = entitlements.lockAccounts(applicant.getUserId(), yearsOf(current));
        CourseApplication application = applications.lockById(applicationId).orElseThrow(NotFoundException::new);
        if (!application.getStatus().isPending()) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED, "Application " + application.getReferenceNo()
                    + " was already " + application.getStatus().label().toLowerCase(Locale.ROOT) + ".");
        }
        requireVersion(application, expectedVersion);

        String fromState = application.getStatus().name();
        AuditEvent event;
        if (decision == Decision.APPROVE) {
            Optional<String> scheduleChange = evaluator.scheduleChange(application);
            if (scheduleChange.isPresent()) {
                throw new BusinessException(ErrorCode.SCHEDULE_OUTDATED, scheduleChange.get());
            }
            application.approve(manager.getUserId(), text, clock.now());
            applications.flush();
            event = record(application, "APPROVED", manager, fromState, text, Map.of());
            for (TrainingEntitlement account : accounts.values()) {
                LedgerDelta held = entitlements.contribution(account.getId(), application.getId());
                entitlements.post(event.getId(), account, application.getId(), null, LedgerEntryType.COMMIT,
                        held.reservedToCommitted());
            }
        } else {
            application.reject(manager.getUserId(), text, clock.now());
            applications.flush();
            event = record(application, "REJECTED", manager, fromState, text, Map.of());
            releaseAll(event, application, accounts);
        }
        notifications.enqueue(event.getId(), application.getApplicantId(),
                decision == Decision.APPROVE ? MailTemplate.APPLICATION_APPROVED : MailTemplate.APPLICATION_REJECTED,
                mailPayload(application, applicant.getName(), text, "/staff/applications/" + application.getId()));
    }

    // ------------------------------------------------------------------ helpers

    private void requireAssignedApprover(User manager, CourseApplication application) {
        if (Objects.equals(application.getApplicantId(), manager.getUserId())) {
            throw new BusinessException(ErrorCode.SELF_APPROVAL, "You cannot decide on your own application.");
        }
        if (!Objects.equals(application.getApproverId(), manager.getUserId())) {
            if (scope.canManagerView(manager, application.getApplicantId(), application.getApproverId(),
                    application.getReviewedBy())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "This application is assigned to another approver.");
            }
            throw new NotFoundException();
        }
    }

    /** Releases whatever the application still holds: its reservation or its approved commitment. */
    private void releaseAll(AuditEvent event, CourseApplication application, Map<Integer, TrainingEntitlement> accounts) {
        for (TrainingEntitlement account : accounts.values()) {
            LedgerDelta held = entitlements.contribution(account.getId(), application.getId());
            LedgerDelta release = new LedgerDelta(held.reservedUnits(), held.committedUnits(), held.reservedAmount(),
                    held.committedAmount(), BigDecimal.ZERO).negate();
            entitlements.post(event.getId(), account, application.getId(), null, LedgerEntryType.RELEASE, release);
        }
    }

    private static LedgerDelta reservationOn(CourseApplication application, int year) {
        int units = application.unitsByYear().getOrDefault(year, 0);
        BigDecimal amount = year == application.getStartDate().getYear() ? application.getCourseFee() : BigDecimal.ZERO;
        return LedgerDelta.reserve(units, amount);
    }

    private CourseApplication ownApplication(User actor, Long applicationId) {
        CourseApplication application = applications.findById(applicationId).orElseThrow(NotFoundException::new);
        scope.requireOwner(actor, application.getApplicantId());
        return application;
    }

    private CourseApplication lockApplication(Long applicationId, Long expectedVersion) {
        CourseApplication application = applications.lockById(applicationId).orElseThrow(NotFoundException::new);
        requireVersion(application, expectedVersion);
        return application;
    }

    private static void requireVersion(CourseApplication application, Long expectedVersion) {
        if (expectedVersion == null || application.getVersion() != expectedVersion) {
            throw new StaleVersionException();
        }
    }

    private static void requirePending(CourseApplication application, String verb) {
        if (!application.getStatus().isPending()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Application " + application.getReferenceNo() + " is "
                    + application.getStatus().label().toLowerCase(Locale.ROOT) + " and can no longer be " + verb + ".");
        }
    }

    private static void requireStatus(CourseApplication application, ApplicationStatus status, String verb) {
        if (application.getStatus() != status) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Application " + application.getReferenceNo() + " is "
                    + application.getStatus().label().toLowerCase(Locale.ROOT) + " and cannot be " + verb + ".");
        }
    }

    private static String requiredText(String value, String field, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, message, Map.of(field, message));
        }
        String trimmed = value.strip();
        if (trimmed.length() > MAX_TEXT) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Use at most 2000 characters.",
                    Map.of(field, "Use at most 2000 characters."));
        }
        return trimmed;
    }

    private AuditEvent record(CourseApplication application, String eventType, User actor, String fromState,
            String reason, Map<String, ?> snapshot) {
        return audit.record(new AuditService.Change(AggregateType.APPLICATION, application.getId().toString(),
                eventType, actor.getUserId(), fromState, application.getStatus().name(), reason, snapshot));
    }

    User lockApplicant(User actor) {
        User applicant = employees.lockById(actor.getUserId()).orElseThrow(NotFoundException::new);
        if (!applicant.isActive()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Your employee record is inactive.");
        }
        return applicant;
    }

    static void requireApplicant(User actor) {
        if (!actor.hasRole(Role.STAFF)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only staff with the employee role can apply for courses.");
        }
    }

    static Set<Integer> yearsOf(ApplicationEvaluator.Evaluation evaluation) {
        Set<Integer> years = new TreeSet<>(evaluation.unitsByYear().keySet());
        years.add(evaluation.details().startDate().getYear());
        return years;
    }

    static Set<Integer> yearsOf(CourseApplication application) {
        Set<Integer> years = new TreeSet<>();
        for (ScheduledDay day : application.getDays()) {
            years.add(day.getTrainingDate().getYear());
        }
        years.add(application.getStartDate().getYear());
        return years;
    }

    static Map<String, Object> snapshot(CourseApplication application) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("reference", application.getReferenceNo());
        snapshot.put("category", application.getCategory());
        snapshot.put("title", application.getCourseTitle());
        snapshot.put("provider", application.getProviderName());
        snapshot.put("start", application.getStartDate() + " " + application.getStartSession());
        snapshot.put("end", application.getEndDate() + " " + application.getEndSession());
        snapshot.put("units", application.totalUnits());
        snapshot.put("fee", application.getCourseFee().toPlainString());
        snapshot.put("approverId", application.getApproverId());
        return snapshot;
    }

    Map<String, Object> mailPayload(CourseApplication application, String applicantName, String reason,
            String targetPath) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reference", application.getReferenceNo());
        payload.put("applicantName", applicantName);
        payload.put("courseTitle", application.getCourseTitle());
        payload.put("period", DAY.format(application.getStartDate()) + " to " + DAY.format(application.getEndDate()));
        payload.put("status", application.getStatus().label());
        payload.put("reason", reason);
        payload.put("link", baseUrl + "/employee/login?next=" + targetPath);
        return payload;
    }
}
