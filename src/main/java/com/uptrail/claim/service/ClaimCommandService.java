package com.uptrail.claim.service;

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

import com.uptrail.application.domain.CourseApplication;
import com.uptrail.application.repository.CourseApplicationRepository;
import com.uptrail.audit.domain.AggregateType;
import com.uptrail.audit.domain.AuditEvent;
import com.uptrail.audit.service.AuditService;
import com.uptrail.claim.domain.ClaimDocument;
import com.uptrail.claim.domain.ClaimStatus;
import com.uptrail.claim.domain.CourseClaim;
import com.uptrail.claim.domain.DocumentType;
import com.uptrail.claim.repository.ClaimDocumentRepository;
import com.uptrail.claim.repository.CourseClaimRepository;
import com.uptrail.claim.service.DocumentStorage.Prepared;
import com.uptrail.claim.service.DocumentStorage.Upload;
import com.uptrail.entitlement.domain.LedgerDelta;
import com.uptrail.entitlement.domain.LedgerEntryType;
import com.uptrail.entitlement.domain.TrainingAccount;
import com.uptrail.entitlement.service.EntitlementService;
import com.uptrail.identity.domain.Actor;
import com.uptrail.identity.domain.Role;
import com.uptrail.notification.domain.MailTemplate;
import com.uptrail.notification.service.NotificationService;
import com.uptrail.organisation.domain.Employee;
import com.uptrail.organisation.repository.EmployeeRepository;
import com.uptrail.organisation.service.AccessScopePolicy;
import com.uptrail.organisation.service.EmployeeDirectoryService;
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

    private final CourseClaimRepository claims;
    private final ClaimDocumentRepository documents;
    private final CourseApplicationRepository applications;
    private final EmployeeRepository employees;
    private final EmployeeDirectoryService directory;
    private final DocumentStorage storage;
    private final EntitlementService entitlements;
    private final AuditService audit;
    private final NotificationService notifications;
    private final AccessScopePolicy scope;
    private final BusinessClock clock;
    private final String baseUrl;

    public ClaimCommandService(CourseClaimRepository claims, ClaimDocumentRepository documents,
            CourseApplicationRepository applications, EmployeeRepository employees, EmployeeDirectoryService directory,
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
    public Long submit(Actor actor, Long applicationId, BigDecimal amount, boolean paidByEmployee, Upload receipt,
            Upload certificate) {
        requireEmployee(actor);
        Employee claimant = lockClaimant(actor);
        if (applicationId == null) {
            throw new NotFoundException();
        }
        CourseApplication application = applications.findById(applicationId).orElseThrow(NotFoundException::new);
        scope.requireOwner(actor, application.getEmployeeId());
        requireEligible(application);
        claims.findByApplicationId(applicationId).ifPresent(existing -> {
            throw new BusinessException(ErrorCode.CLAIM_NOT_ELIGIBLE, "Application " + application.getReferenceNo()
                    + " already has a fee claim. Open it to follow it up or to revise it after a rejection.");
        });
        Map<DocumentType, Prepared> files = checkContent(application, amount, paidByEmployee, receipt, certificate);
        Long approverId = currentApprover(claimant);

        CourseClaim claim = claims.save(CourseClaim.submit(applicationId, approverId, money(amount), clock.now()));
        claims.flush();
        storeDocuments(claim, files, actor);
        AuditEvent event = record(claim, "SUBMITTED", actor, null, null, snapshot(claim, application, files));
        notifications.enqueue(event.getId(), approverId, MailTemplate.CLAIM_SUBMITTED,
                mailPayload(claim, application, claimant.getFullName(), null, "/manager/claims/" + claim.getId()));
        return claim.getId();
    }

    /** Revises a rejected claim: same claim, next revision, both documents again. */
    @WriteTransaction
    public void resubmit(Actor actor, Long claimId, Long expectedVersion, BigDecimal amount, boolean paidByEmployee,
            Upload receipt, Upload certificate) {
        requireEmployee(actor);
        Employee claimant = lockClaimant(actor);
        CourseClaim current = claims.findById(claimId).orElseThrow(NotFoundException::new);
        CourseApplication application = applications.findById(current.getApplicationId()).orElseThrow();
        scope.requireOwner(actor, application.getEmployeeId());
        CourseClaim claim = claims.lockById(claimId).orElseThrow(NotFoundException::new);
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
                mailPayload(claim, application, claimant.getFullName(), null, "/manager/claims/" + claim.getId()));
    }

    // ------------------------------------------------------------------ manager

    /** Approves or rejects a submitted claim assigned to the acting manager; a reason is required. */
    @WriteTransaction
    public void decide(Actor manager, Long claimId, Decision decision, String reason, Long expectedVersion) {
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
        Employee owner = employees.lockById(ownerId).orElseThrow(NotFoundException::new);
        CourseClaim current = claims.findById(claimId).orElseThrow(NotFoundException::new);
        requireAssignedApprover(manager, current, ownerId);
        CourseClaim claim = claims.lockById(claimId).orElseThrow(NotFoundException::new);
        CourseApplication application = applications.findById(claim.getApplicationId()).orElseThrow();
        if (claim.getStatus() != ClaimStatus.SUBMITTED) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED, "The claim for " + application.getReferenceNo()
                    + " was already " + claim.getStatus().label().toLowerCase(Locale.ROOT) + ".");
        }
        requireVersion(claim, expectedVersion);

        if (decision == Decision.APPROVE) {
            claim.approve(manager.employeeId(), text, clock.now());
        } else {
            claim.reject(manager.employeeId(), text, clock.now());
        }
        claims.flush();
        AuditEvent event = record(claim, claim.getStatus().name(), manager, ClaimStatus.SUBMITTED.name(), text,
                Map.of("amount", claim.getAmount().toPlainString(), "revision", claim.getRevision()));
        notifications.enqueue(event.getId(), ownerId,
                decision == Decision.APPROVE ? MailTemplate.CLAIM_APPROVED : MailTemplate.CLAIM_REJECTED,
                mailPayload(claim, application, owner.getFullName(), text, "/employee/claims/" + claim.getId()));
    }

    // ------------------------------------------------------------------ administrator

    /**
     * Records that an approved claim was reimbursed. Uptrail does not move money: the reference is a
     * simulated one ({@code SIM-yyyyMMdd-<claim id>}). A second registration is refused with 409.
     */
    @WriteTransaction
    public String registerReimbursement(Actor admin, Long claimId, Long expectedVersion) {
        if (!admin.hasRole(Role.ADMIN)) {
            throw new NotFoundException();
        }
        Long ownerId = claims.findEmployeeIdById(claimId).orElseThrow(NotFoundException::new);
        if (Objects.equals(ownerId, admin.employeeId())) {
            throw new BusinessException(ErrorCode.SELF_APPROVAL,
                    "You cannot register the reimbursement of your own claim.");
        }
        Employee owner = employees.lockById(ownerId).orElseThrow(NotFoundException::new);
        CourseClaim current = claims.findById(claimId).orElseThrow(NotFoundException::new);
        requireVisibleToAdministrator(current);
        CourseApplication application = applications.findById(current.getApplicationId()).orElseThrow();
        int year = application.getStartDate().getYear();
        Map<Integer, TrainingAccount> accounts = entitlements.lockAccounts(ownerId, Set.of(year));
        CourseClaim claim = claims.lockById(claimId).orElseThrow(NotFoundException::new);
        if (claim.getStatus() == ClaimStatus.REIMBURSED) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED, "This claim was already registered as reimbursed "
                    + "with reference " + claim.getReimbursementReference() + ".");
        }
        requireVisibleToAdministrator(claim);
        requireVersion(claim, expectedVersion);

        String reference = "SIM-" + REFERENCE_DATE.format(clock.today()) + "-" + String.format("%06d", claim.getId());
        claim.registerReimbursement(admin.employeeId(), reference, clock.now());
        claims.flush();
        AuditEvent event = record(claim, "REIMBURSED", admin, ClaimStatus.APPROVED.name(),
                "Reimbursement registered (simulated, no payment initiated)",
                Map.of("reference", reference, "amount", claim.getAmount().toPlainString(), "year", year));
        entitlements.post(event.getId(), accounts.get(year), application.getId(), claim.getId(),
                LedgerEntryType.REIMBURSE, LedgerDelta.reimburse(claim.getAmount()));
        notifications.enqueue(event.getId(), ownerId, MailTemplate.CLAIM_REIMBURSED,
                mailPayload(claim, application, owner.getFullName(), null, "/employee/claims/" + claim.getId()));
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

    private void storeDocuments(CourseClaim claim, Map<DocumentType, Prepared> files, Actor actor) {
        files.forEach((type, prepared) -> {
            String key = storage.save(prepared);
            documents.save(ClaimDocument.record(claim.getId(), claim.getRevision(), type, key,
                    prepared.originalName(), prepared.format().contentType(), prepared.size(), prepared.sha256(),
                    actor.employeeId(), clock.now()));
        });
        documents.flush();
    }

    private static void requireEligible(CourseApplication application) {
        ClaimPolicy.ineligibility(application).ifPresent(reason -> {
            throw new BusinessException(ErrorCode.CLAIM_NOT_ELIGIBLE, reason);
        });
    }

    private Long currentApprover(Employee claimant) {
        Long approverId = directory.approverOf(claimant.getId()).orElseThrow(() -> new BusinessException(
                ErrorCode.MISSING_APPROVER, "You have no approving manager. Ask an administrator to assign one."));
        if (approverId.equals(claimant.getId())) {
            throw new BusinessException(ErrorCode.SELF_APPROVAL, "You cannot approve your own claim.");
        }
        return approverId;
    }

    private void requireAssignedApprover(Actor manager, CourseClaim claim, Long ownerId) {
        if (Objects.equals(ownerId, manager.employeeId())) {
            throw new BusinessException(ErrorCode.SELF_APPROVAL, "You cannot decide on your own claim.");
        }
        if (!Objects.equals(claim.getApproverId(), manager.employeeId())) {
            if (scope.canManagerView(manager, ownerId, claim.getApproverId(), claim.getReviewedBy())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "This claim is assigned to another approver.");
            }
            throw new NotFoundException();
        }
    }

    /** Administrators work only with claims that are approved or already reimbursed. */
    static void requireVisibleToAdministrator(CourseClaim claim) {
        if (claim.getStatus() != ClaimStatus.APPROVED && claim.getStatus() != ClaimStatus.REIMBURSED) {
            throw new NotFoundException();
        }
    }

    private Employee lockClaimant(Actor actor) {
        Employee claimant = employees.lockById(actor.employeeId()).orElseThrow(NotFoundException::new);
        if (!claimant.isActive()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Your employee record is inactive.");
        }
        return claimant;
    }

    private static void requireEmployee(Actor actor) {
        if (!actor.hasRole(Role.EMPLOYEE)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only staff with the employee role can claim fees.");
        }
    }

    private static void requireVersion(CourseClaim claim, Long expectedVersion) {
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

    private AuditEvent record(CourseClaim claim, String eventType, Actor actor, String fromState, String reason,
            Map<String, ?> snapshot) {
        return audit.record(new AuditService.Change(AggregateType.CLAIM, claim.getId().toString(), eventType,
                actor.employeeId(), fromState, claim.getStatus().name(), reason, snapshot));
    }

    private static Map<String, Object> snapshot(CourseClaim claim, CourseApplication application,
            Map<DocumentType, Prepared> files) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("application", application.getReferenceNo());
        snapshot.put("revision", claim.getRevision());
        snapshot.put("amount", claim.getAmount().toPlainString());
        files.forEach((type, prepared) -> snapshot.put(type.name().toLowerCase(Locale.ROOT),
                prepared.originalName() + " (" + prepared.size() + " bytes, sha256 " + prepared.sha256() + ")"));
        return snapshot;
    }

    private Map<String, Object> mailPayload(CourseClaim claim, CourseApplication application, String claimantName,
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
        payload.put("link", baseUrl + "/login?next=" + targetPath);
        return payload;
    }
}
