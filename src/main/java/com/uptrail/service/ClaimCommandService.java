package com.uptrail.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.uptrail.model.CourseApplication;
import com.uptrail.repo.CourseApplicationRepo;
import com.uptrail.model.AggregateType;
import com.uptrail.model.AuditEvent;
import com.uptrail.model.ClaimDocument;
import com.uptrail.model.ClaimStatus;
import com.uptrail.model.CourseFeeApplication;
import com.uptrail.model.DocumentType;
import com.uptrail.repo.ClaimDocumentRepo;
import com.uptrail.repo.CourseFeeApplicationRepo;
import com.uptrail.service.DocumentStorage.Prepared;
import com.uptrail.service.DocumentStorage.Upload;
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
 * Fee claim commands. They follow the write protocol of applications: lock the claimant's employee row,
 * then (for reimbursement) the annual account of the course's start year, then the claim; re-check status
 * and version inside the locks; write the claim, its documents, one audit event, the ledger change and the
 * outbox message in one transaction. Claim decisions do not change the ledger; only a registered
 * reimbursement adds a REIMBURSE row, which never reduces the budget a second time.
 */
@Service
public class ClaimCommandService {

    public enum Decision {
        APPROVE,
        REJECT
    }

    private static final int MAX_TEXT = 2000;
    private static final DateTimeFormatter REFERENCE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ENGLISH);

    private final CourseFeeApplicationRepo claims;
    private final ClaimDocumentRepo documents;
    private final CourseApplicationRepo applications;
    private final UserRepo employees;
    private final StaffService directory;
    private final DocumentStorage storage;
    private final EntitlementService entitlements;
    private final AuditService audit;
    private final NotificationService notifications;
    private final AccessScopePolicy scope;
    private final BusinessClock clock;
    private final String baseUrl;

    public ClaimCommandService(CourseFeeApplicationRepo claims, ClaimDocumentRepo documents,
            CourseApplicationRepo applications, UserRepo employees, StaffService directory,
            DocumentStorage storage, EntitlementService entitlements, AuditService audit,
            NotificationService notifications, AccessScopePolicy scope, BusinessClock clock,
            @Value("${uptrail.base-url}") String baseUrl) {
        this.claims = claims;
        this.documents = documents;
        this.applications = applications;
        this.employees = employees;
        this.directory = directory;
        this.storage = storage;
        this.entitlements = entitlements;
        this.audit = audit;
        this.notifications = notifications;
        this.scope = scope;
        this.clock = clock;
        this.baseUrl = baseUrl;
    }

    // ------------------------------------------------------------------ employee

    /** Submits the single fee claim of a completed, fee-paying application with its two documents. */
    @WriteTransaction
    public Long submit(User actor, Long applicationId, BigDecimal amount, boolean paidByEmployee, Upload receipt,
            Upload certificate) {
        requireEmployee(actor);
        User claimant = lockClaimant(actor);
        if (applicationId == null) {
            throw new NotFoundException();
        }
        CourseApplication application = applications.findById(applicationId).orElseThrow(NotFoundException::new);
        scope.requireOwner(actor, application.getApplicantId());
        requireEligible(application);
        claims.findByApplicationId(applicationId).ifPresent(existing -> {
            throw new BusinessException(ErrorCode.CLAIM_NOT_ELIGIBLE, "Application " + application.getReferenceNo()
                    + " already has a fee claim. Open it to follow it up or to revise it after a rejection.");
        });
        Map<DocumentType, Prepared> files = checkContent(application, amount, paidByEmployee, receipt, certificate);
        Long approverId = currentApprover(claimant);

        CourseFeeApplication claim = claims.save(CourseFeeApplication.submit(applicationId, approverId, money(amount), clock.now()));
        claims.flush();
        storeDocuments(claim, files, actor);
        AuditEvent event = record(claim, "SUBMITTED", actor, null, null, snapshot(claim, application, files));
        notifications.enqueue(event.getId(), approverId, MailTemplate.CLAIM_SUBMITTED,
                mailPayload(claim, application, claimant.getName(), null, "/manager/claims/" + claim.getId()));
        return claim.getId();
    }

    /** Revises a rejected claim: same claim, next revision, both documents again. */
    @WriteTransaction
    public void resubmit(User actor, Long claimId, Long expectedVersion, BigDecimal amount, boolean paidByEmployee,
            Upload receipt, Upload certificate) {
        requireEmployee(actor);
        User claimant = lockClaimant(actor);
        CourseFeeApplication current = claims.findById(claimId).orElseThrow(NotFoundException::new);
        CourseApplication application = applications.findById(current.getApplicationId()).orElseThrow();
        scope.requireOwner(actor, application.getApplicantId());
        CourseFeeApplication claim = claims.lockById(claimId).orElseThrow(NotFoundException::new);
        if (claim.getStatus() != ClaimStatus.REJECTED) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This claim is "
                    + claim.getStatus().label().toLowerCase(Locale.ROOT) + "; only a rejected claim can be revised.");
        }
        requireVersion(claim, expectedVersion);
        requireEligible(application);
        Map<DocumentType, Prepared> files = checkContent(application, amount, paidByEmployee, receipt, certificate);
        Long approverId = currentApprover(claimant);

        claim.resubmit(approverId, money(amount), clock.now());
        claims.flush();
        storeDocuments(claim, files, actor);
        AuditEvent event = record(claim, "RESUBMITTED", actor, ClaimStatus.REJECTED.name(), null,
                snapshot(claim, application, files));
        notifications.enqueue(event.getId(), approverId, MailTemplate.CLAIM_SUBMITTED,
                mailPayload(claim, application, claimant.getName(), null, "/manager/claims/" + claim.getId()));
    }

    // ------------------------------------------------------------------ manager

    /** Approves or rejects a submitted claim assigned to the acting manager; a reason is required. */
    @WriteTransaction
    public void decide(User manager, Long claimId, Decision decision, String reason, Long expectedVersion) {
        if (!manager.hasRole(Role.MANAGER)) {
            throw new NotFoundException();
        }
        if (decision == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Choose Approve or Reject.");
        }
        String text = requiredText(reason, decision == Decision.APPROVE
                ? "Give the reason for approving the claim."
                : "Give the reason for rejecting, so the claimant knows what to correct.");
        Long ownerId = claims.findEmployeeIdById(claimId).orElseThrow(NotFoundException::new);
        User owner = employees.lockById(ownerId).orElseThrow(NotFoundException::new);
        CourseFeeApplication current = claims.findById(claimId).orElseThrow(NotFoundException::new);
        requireAssignedApprover(manager, current, ownerId);
        CourseFeeApplication claim = claims.lockById(claimId).orElseThrow(NotFoundException::new);
        CourseApplication application = applications.findById(claim.getApplicationId()).orElseThrow();
        if (claim.getStatus() != ClaimStatus.SUBMITTED) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED, "The claim for " + application.getReferenceNo()
                    + " was already " + claim.getStatus().label().toLowerCase(Locale.ROOT) + ".");
        }
        requireVersion(claim, expectedVersion);

        if (decision == Decision.APPROVE) {
            claim.approve(manager.getUserId(), text, clock.now());
        } else {
            claim.reject(manager.getUserId(), text, clock.now());
        }
        claims.flush();
        AuditEvent event = record(claim, claim.getStatus().name(), manager, ClaimStatus.SUBMITTED.name(), text,
                Map.of("amount", claim.getAmount().toPlainString(), "revision", claim.getRevision()));
        notifications.enqueue(event.getId(), ownerId,
                decision == Decision.APPROVE ? MailTemplate.CLAIM_APPROVED : MailTemplate.CLAIM_REJECTED,
                mailPayload(claim, application, owner.getName(), text, "/staff/claims/" + claim.getId()));
    }

    // ------------------------------------------------------------------ administrator

    /**
     * Records that an approved claim was reimbursed. Uptrail does not move money: the reference is a
     * simulated one ({@code SIM-yyyyMMdd-<claim id>}). A second registration is refused with 409.
     */
    @WriteTransaction
    public String registerReimbursement(User admin, Long claimId, Long expectedVersion) {
        if (!admin.hasRole(Role.ADMIN)) {
            throw new NotFoundException();
        }
        Long ownerId = claims.findEmployeeIdById(claimId).orElseThrow(NotFoundException::new);
        if (Objects.equals(ownerId, admin.getUserId())) {
            throw new BusinessException(ErrorCode.SELF_APPROVAL,
                    "You cannot register the reimbursement of your own claim.");
        }
        User owner = employees.lockById(ownerId).orElseThrow(NotFoundException::new);
        CourseFeeApplication current = claims.findById(claimId).orElseThrow(NotFoundException::new);
        requireVisibleToAdministrator(current);
        CourseApplication application = applications.findById(current.getApplicationId()).orElseThrow();
        int year = application.getStartDate().getYear();
        Map<Integer, TrainingEntitlement> accounts = entitlements.lockAccounts(ownerId, Set.of(year));
        CourseFeeApplication claim = claims.lockById(claimId).orElseThrow(NotFoundException::new);
        if (claim.getStatus() == ClaimStatus.REIMBURSED) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED, "This claim was already registered as reimbursed "
                    + "with reference " + claim.getReimbursementReference() + ".");
        }
        requireVisibleToAdministrator(claim);
        requireVersion(claim, expectedVersion);

        String reference = "SIM-" + REFERENCE_DATE.format(clock.today()) + "-" + String.format("%06d", claim.getId());
        claim.registerReimbursement(admin.getUserId(), reference, clock.now());
        claims.flush();
        AuditEvent event = record(claim, "REIMBURSED", admin, ClaimStatus.APPROVED.name(),
                "Reimbursement registered (simulated, no payment initiated)",
                Map.of("reference", reference, "amount", claim.getAmount().toPlainString(), "year", year));
        entitlements.post(event.getId(), accounts.get(year), application.getId(), claim.getId(),
                LedgerEntryType.REIMBURSE, LedgerDelta.reimburse(claim.getAmount()));
        notifications.enqueue(event.getId(), ownerId, MailTemplate.CLAIM_REIMBURSED,
                mailPayload(claim, application, owner.getName(), null, "/staff/claims/" + claim.getId()));
        return reference;
    }

    // ------------------------------------------------------------------ helpers

    private Map<DocumentType, Prepared> checkContent(CourseApplication application, BigDecimal amount,
            boolean paidByEmployee, Upload receipt, Upload certificate) {
        if (!paidByEmployee) {
            String message = "Only a fee you paid yourself can be claimed. Confirm that you paid it, or do not claim.";
            throw new BusinessException(ErrorCode.CLAIM_NOT_ELIGIBLE, message, Map.of("paidByEmployee", message));
        }
        Map<String, String> problems = new LinkedHashMap<>();
        ClaimPolicy.amountProblem(amount, application.getCourseFee()).ifPresent(p -> problems.put("amount", p));
        Map<DocumentType, Prepared> files = new EnumMap<>(DocumentType.class);
        DocumentStorage.Result receiptResult = storage.prepare(receipt);
        DocumentStorage.Result certificateResult = storage.prepare(certificate);
        if (receiptResult.isOk()) {
            files.put(DocumentType.RECEIPT, receiptResult.prepared());
        } else {
            problems.put("receipt", "Receipt: " + receiptResult.problem());
        }
        if (certificateResult.isOk()) {
            files.put(DocumentType.COMPLETION_CERTIFICATE, certificateResult.prepared());
        } else {
            problems.put("certificate", "Certificate of completion: " + certificateResult.problem());
        }
        if (!problems.isEmpty()) {
            ErrorCode code = problems.containsKey("amount") ? ErrorCode.VALIDATION_FAILED : ErrorCode.INVALID_DOCUMENT;
            String message = problems.size() == 1 ? problems.values().iterator().next()
                    : "Correct the highlighted fields. Files have to be chosen again after a failed submission.";
            throw new BusinessException(code, message, problems);
        }
        return files;
    }

    private void storeDocuments(CourseFeeApplication claim, Map<DocumentType, Prepared> files, User actor) {
        files.forEach((type, prepared) -> {
            String key = storage.save(prepared);
            documents.save(ClaimDocument.record(claim.getId(), claim.getRevision(), type, key,
                    prepared.originalName(), prepared.format().contentType(), prepared.size(), prepared.sha256(),
                    actor.getUserId(), clock.now()));
        });
        documents.flush();
    }

    private static void requireEligible(CourseApplication application) {
        ClaimPolicy.ineligibility(application).ifPresent(reason -> {
            throw new BusinessException(ErrorCode.CLAIM_NOT_ELIGIBLE, reason);
        });
    }

    private Long currentApprover(User claimant) {
        Long approverId = directory.approverOf(claimant.getUserId()).orElseThrow(() -> new BusinessException(
                ErrorCode.MISSING_APPROVER, "You have no approving manager. Ask an administrator to assign one."));
        if (approverId.equals(claimant.getUserId())) {
            throw new BusinessException(ErrorCode.SELF_APPROVAL, "You cannot approve your own claim.");
        }
        return approverId;
    }

    private void requireAssignedApprover(User manager, CourseFeeApplication claim, Long ownerId) {
        if (Objects.equals(ownerId, manager.getUserId())) {
            throw new BusinessException(ErrorCode.SELF_APPROVAL, "You cannot decide on your own claim.");
        }
        if (!Objects.equals(claim.getApproverId(), manager.getUserId())) {
            if (scope.canManagerView(manager, ownerId, claim.getApproverId(), claim.getReviewedBy())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "This claim is assigned to another approver.");
            }
            throw new NotFoundException();
        }
    }

    /** Administrators work only with claims that are approved or already reimbursed. */
    static void requireVisibleToAdministrator(CourseFeeApplication claim) {
        if (claim.getStatus() != ClaimStatus.APPROVED && claim.getStatus() != ClaimStatus.REIMBURSED) {
            throw new NotFoundException();
        }
    }

    private User lockClaimant(User actor) {
        User claimant = employees.lockById(actor.getUserId()).orElseThrow(NotFoundException::new);
        if (!claimant.isActive()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Your employee record is inactive.");
        }
        return claimant;
    }

    private static void requireEmployee(User actor) {
        if (!actor.hasRole(Role.STAFF)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only staff with the employee role can claim fees.");
        }
    }

    private static void requireVersion(CourseFeeApplication claim, Long expectedVersion) {
        if (expectedVersion == null || claim.getVersion() != expectedVersion) {
            throw new StaleVersionException();
        }
    }

    private static String requiredText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, message, Map.of("reason", message));
        }
        String trimmed = value.strip();
        if (trimmed.length() > MAX_TEXT) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Use at most 2000 characters.",
                    Map.of("reason", "Use at most 2000 characters."));
        }
        return trimmed;
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private AuditEvent record(CourseFeeApplication claim, String eventType, User actor, String fromState, String reason,
            Map<String, ?> snapshot) {
        return audit.record(new AuditService.Change(AggregateType.CLAIM, claim.getId().toString(), eventType,
                actor.getUserId(), fromState, claim.getStatus().name(), reason, snapshot));
    }

    private static Map<String, Object> snapshot(CourseFeeApplication claim, CourseApplication application,
            Map<DocumentType, Prepared> files) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("application", application.getReferenceNo());
        snapshot.put("revision", claim.getRevision());
        snapshot.put("amount", claim.getAmount().toPlainString());
        files.forEach((type, prepared) -> snapshot.put(type.name().toLowerCase(Locale.ROOT),
                prepared.originalName() + " (" + prepared.size() + " bytes, sha256 " + prepared.sha256() + ")"));
        return snapshot;
    }

    private Map<String, Object> mailPayload(CourseFeeApplication claim, CourseApplication application, String claimantName,
            String reason, String targetPath) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reference", application.getReferenceNo());
        payload.put("claimantName", claimantName);
        payload.put("courseTitle", application.getCourseTitle());
        payload.put("amount", "SGD " + claim.getAmount().toPlainString());
        payload.put("revision", claim.getRevision());
        payload.put("status", claim.getStatus().label());
        payload.put("reason", reason);
        payload.put("reimbursementReference", claim.getReimbursementReference());
        payload.put("link", baseUrl + "/employee/login?next=" + targetPath);
        return payload;
    }
}
