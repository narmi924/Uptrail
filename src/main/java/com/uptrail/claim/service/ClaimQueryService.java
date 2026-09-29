package com.uptrail.claim.service;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uptrail.application.domain.CourseApplication;
import com.uptrail.application.repository.CourseApplicationRepository;
import com.uptrail.application.service.ApplicationViews.TimelineEntry;
import com.uptrail.audit.domain.AggregateType;
import com.uptrail.audit.domain.AuditEvent;
import com.uptrail.audit.repository.AuditEventRepository;
import com.uptrail.catalogue.domain.CategoryCode;
import com.uptrail.catalogue.service.CatalogueQueryService;
import com.uptrail.claim.domain.ClaimDocument;
import com.uptrail.claim.domain.ClaimStatus;
import com.uptrail.claim.domain.CourseClaim;
import com.uptrail.claim.domain.DocumentType;
import com.uptrail.claim.repository.ClaimDocumentRepository;
import com.uptrail.claim.repository.CourseClaimRepository;
import com.uptrail.entitlement.domain.Session;
import com.uptrail.identity.domain.Actor;
import com.uptrail.identity.domain.Role;
import com.uptrail.organisation.service.AccessScopePolicy;
import com.uptrail.organisation.service.EmployeeDirectoryService;
import com.uptrail.shared.error.NotFoundException;

/**
 * Reads of fee claims for the three workspaces, with the data scope of each: claimants see their own claims,
 * managers the claims assigned to or decided by them, administrators approved and reimbursed claims.
 * Document downloads are authorised here as well.
 */
@Service
@Transactional(readOnly = true)
public class ClaimQueryService {

    public record ClaimRow(Long id, Long applicationId, String applicationReference, Long employeeId,
            String employeeName, String department, String courseTitle, String categoryName, BigDecimal fee,
            BigDecimal amount, int revision, ClaimStatus status, Instant submittedAt, Instant lastActivity,
            String approverName, String reviewerName, String reviewComment, Instant reviewedAt,
            String reimbursementReference, Instant reimbursedAt) {
    }

    public record ApplicationSummary(Long id, String referenceNo, String courseTitle, CategoryCode category,
            String categoryName, String providerName, LocalDate startDate, Session startSession, LocalDate endDate,
            Session endSession, BigDecimal fee, Instant completedAt, String completionComment) {
    }

    public record DocumentView(Long id, DocumentType type, String originalName, String contentType, long sizeBytes,
            Instant uploadedAt) {
    }

    public record RevisionView(int revision, boolean current, List<DocumentView> documents) {
    }

    public record Actions(boolean canResubmit, boolean canDecide, String decisionNote, boolean canRegister,
            String registerNote) {
    }

    public record Detail(Long id, long version, ClaimStatus status, int revision, BigDecimal amount,
            boolean paidByEmployee, Instant submittedAt, Long employeeId, String employeeName, String department,
            String approverName, String reviewerName, Instant reviewedAt, String reviewComment,
            String reimbursedByName, Instant reimbursedAt, String reimbursementReference,
            ApplicationSummary application, List<RevisionView> revisions, List<TimelineEntry> timeline,
            Actions actions) {
    }

    /** A completed course the employee may still claim, or the claim that already exists for it. */
    public record NewClaim(ApplicationSummary application, Long existingClaimId, String ineligibleReason) {
    }

    public record DocumentFile(Path path, String fileName, String contentType, long sizeBytes) {
    }

    private static final Map<String, String> EVENT_LABELS = Map.of(
            "SUBMITTED", "Claim submitted", "RESUBMITTED", "Revised claim submitted", "APPROVED", "Claim approved",
            "REJECTED", "Claim rejected", "REIMBURSED", "Reimbursement registered",
            "REASSIGNED", "Approver reassigned");

    private final CourseClaimRepository claims;
    private final ClaimDocumentRepository documents;
    private final CourseApplicationRepository applications;
    private final AuditEventRepository auditEvents;
    private final EmployeeDirectoryService directory;
    private final CatalogueQueryService catalogue;
    private final AccessScopePolicy scope;
    private final DocumentStorage storage;

    public ClaimQueryService(CourseClaimRepository claims, ClaimDocumentRepository documents,
            CourseApplicationRepository applications, AuditEventRepository auditEvents,
            EmployeeDirectoryService directory, CatalogueQueryService catalogue, AccessScopePolicy scope,
            DocumentStorage storage) {
        this.claims = claims;
        this.documents = documents;
        this.applications = applications;
        this.auditEvents = auditEvents;
        this.directory = directory;
        this.catalogue = catalogue;
        this.scope = scope;
        this.storage = storage;
    }

    public long maxDocumentBytes() {
        return storage.maxBytes();
    }

    // ------------------------------------------------------------------ employee

    public Page<ClaimRow> ownClaims(Actor actor, Pageable pageable) {
        return rows(claims.findOwn(actor.employeeId(), pageable));
    }

    public List<ApplicationSummary> claimable(Actor actor) {
        List<CategoryCode> feePaying = EnumSet.allOf(CategoryCode.class).stream()
                .filter(CategoryCode::isFeePaying).toList();
        return applications.findClaimable(actor.employeeId(), feePaying).stream().map(this::summary).toList();
    }

    public NewClaim newClaim(Actor actor, Long applicationId) {
        CourseApplication application = applications.findById(applicationId).orElseThrow(NotFoundException::new);
        scope.requireOwner(actor, application.getEmployeeId());
        Long existing = claims.findByApplicationId(applicationId).map(CourseClaim::getId).orElse(null);
        return new NewClaim(summary(application), existing, ClaimPolicy.ineligibility(application).orElse(null));
    }

    public Detail ownDetail(Actor actor, Long claimId) {
        CourseClaim claim = claims.findById(claimId).orElseThrow(NotFoundException::new);
        CourseApplication application = applications.findById(claim.getApplicationId()).orElseThrow();
        scope.requireOwner(actor, application.getEmployeeId());
        boolean canResubmit = claim.getStatus() == ClaimStatus.REJECTED
                && ClaimPolicy.ineligibility(application).isEmpty();
        return detail(claim, application, new Actions(canResubmit, false, null, false, null));
    }

    // ------------------------------------------------------------------ manager

    public Page<ClaimRow> pendingForManager(Actor manager, Pageable pageable) {
        requireManager(manager);
        return rows(claims.findByApproverIdAndStatusOrderBySubmittedAtAscIdAsc(manager.employeeId(),
                ClaimStatus.SUBMITTED, pageable));
    }

    public long pendingCount(Actor manager) {
        return claims.countByApproverIdAndStatus(manager.employeeId(), ClaimStatus.SUBMITTED);
    }

    public Detail managerDetail(Actor manager, Long claimId) {
        requireManager(manager);
        CourseClaim claim = claims.findById(claimId).orElseThrow(NotFoundException::new);
        CourseApplication application = applications.findById(claim.getApplicationId()).orElseThrow();
        if (!scope.canManagerView(manager, application.getEmployeeId(), claim.getApproverId(),
                claim.getReviewedBy())) {
            throw new NotFoundException();
        }
        boolean assigned = Objects.equals(claim.getApproverId(), manager.employeeId());
        boolean canDecide = claim.getStatus() == ClaimStatus.SUBMITTED && assigned;
        String note = null;
        if (!canDecide) {
            note = claim.getStatus() != ClaimStatus.SUBMITTED ? "This claim was already decided."
                    : "This claim is assigned to " + directory.nameOf(claim.getApproverId()) + ".";
        }
        return detail(claim, application, new Actions(false, canDecide, note, false, null));
    }

    // ------------------------------------------------------------------ administrator

    public Page<ClaimRow> awaitingReimbursement(Pageable pageable) {
        return rows(claims.findByStatusOrderByReviewedAtAscIdAsc(ClaimStatus.APPROVED, pageable));
    }

    public Page<ClaimRow> reimbursed(Pageable pageable) {
        return rows(claims.findByStatusOrderByReimbursedAtDescIdDesc(ClaimStatus.REIMBURSED, pageable));
    }

    public long awaitingCount() {
        return claims.countByStatus(ClaimStatus.APPROVED);
    }

    public Detail adminDetail(Actor admin, Long claimId) {
        requireAdmin(admin);
        CourseClaim claim = claims.findById(claimId).orElseThrow(NotFoundException::new);
        ClaimCommandService.requireVisibleToAdministrator(claim);
        CourseApplication application = applications.findById(claim.getApplicationId()).orElseThrow();
        boolean own = Objects.equals(application.getEmployeeId(), admin.employeeId());
        boolean approved = claim.getStatus() == ClaimStatus.APPROVED;
        String note = !approved ? "The reimbursement was already registered."
                : own ? "Another administrator has to register the reimbursement of your own claim." : null;
        return detail(claim, application, new Actions(false, false, null, approved && !own, note));
    }

    // ------------------------------------------------------------------ documents

    /**
     * The file of a claim document if the actor may download it in the given workspace: the claimant, the
     * manager the claim is assigned to or who decided it, or (administration workspace) an administrator
     * once the claim is approved. Anything else is reported as not found.
     */
    public DocumentFile document(Actor actor, Long documentId, boolean administrationWorkspace) {
        ClaimDocument document = documents.findById(documentId).orElseThrow(NotFoundException::new);
        CourseClaim claim = claims.findById(document.getClaimId()).orElseThrow(NotFoundException::new);
        CourseApplication application = applications.findById(claim.getApplicationId()).orElseThrow();
        boolean allowed;
        if (administrationWorkspace) {
            allowed = actor.hasRole(Role.ADMIN) && (claim.getStatus() == ClaimStatus.APPROVED
                    || claim.getStatus() == ClaimStatus.REIMBURSED);
        } else {
            allowed = Objects.equals(application.getEmployeeId(), actor.employeeId())
                    || (actor.hasRole(Role.MANAGER) && (Objects.equals(claim.getApproverId(), actor.employeeId())
                            || Objects.equals(claim.getReviewedBy(), actor.employeeId())));
        }
        if (!allowed) {
            throw new NotFoundException();
        }
        return new DocumentFile(storage.open(document.getStorageKey()), document.getOriginalName(),
                document.getDetectedContentType(), document.getSizeBytes());
    }

    // ------------------------------------------------------------------ mapping

    private Detail detail(CourseClaim claim, CourseApplication application, Actions actions) {
        List<AuditEvent> events = auditEvents.findByAggregateTypeAndAggregateKeyOrderByCreatedAtDescIdDesc(
                AggregateType.CLAIM, claim.getId().toString());
        Set<Long> people = new HashSet<>();
        Stream.of(application.getEmployeeId(), claim.getApproverId(), claim.getReviewedBy(), claim.getReimbursedBy())
                .filter(Objects::nonNull).forEach(people::add);
        events.stream().map(AuditEvent::getActorEmployeeId).filter(Objects::nonNull).forEach(people::add);
        Map<Long, String> names = directory.namesOf(people);
        List<TimelineEntry> timeline = events.stream().map(e -> new TimelineEntry(e.getCreatedAt(),
                e.getActorEmployeeId() == null ? "System" : names.getOrDefault(e.getActorEmployeeId(), "Unknown"),
                EVENT_LABELS.getOrDefault(e.getEventType(), e.getEventType()), e.getFromState(), e.getToState(),
                e.getReason())).toList();

        Map<Integer, List<DocumentView>> byRevision = new LinkedHashMap<>();
        for (ClaimDocument d : documents.findByClaimIdOrderByClaimRevisionDescDocumentTypeAsc(claim.getId())) {
            byRevision.computeIfAbsent(d.getClaimRevision(), r -> new ArrayList<>()).add(new DocumentView(d.getId(),
                    d.getDocumentType(), d.getOriginalName(), d.getDetectedContentType(), d.getSizeBytes(),
                    d.getUploadedAt()));
        }
        // Receipt first, then the certificate, within each revision (newest revision first).
        byRevision.values().forEach(list -> list.sort(java.util.Comparator.comparing(d -> d.type().ordinal())));
        List<RevisionView> revisions = byRevision.entrySet().stream()
                .map(e -> new RevisionView(e.getKey(), e.getKey() == claim.getRevision(), e.getValue())).toList();
        String department = directory.card(application.getEmployeeId()).department();
        return new Detail(claim.getId(), claim.getVersion(), claim.getStatus(), claim.getRevision(), claim.getAmount(),
                claim.isPaidByEmployee(), claim.getSubmittedAt(), application.getEmployeeId(),
                names.get(application.getEmployeeId()), department, names.get(claim.getApproverId()),
                claim.getReviewedBy() == null ? null : names.get(claim.getReviewedBy()), claim.getReviewedAt(),
                claim.getReviewComment(), claim.getReimbursedBy() == null ? null : names.get(claim.getReimbursedBy()),
                claim.getReimbursedAt(), claim.getReimbursementReference(), summary(application), revisions, timeline,
                actions);
    }

    private ApplicationSummary summary(CourseApplication a) {
        return new ApplicationSummary(a.getId(), a.getReferenceNo(), a.getCourseTitle(), a.getCategory(),
                catalogue.categoryNames().getOrDefault(a.getCategory(), a.getCategory().defaultLabel()),
                a.getProviderName(), a.getStartDate(), a.getStartSession(), a.getEndDate(), a.getEndSession(),
                a.getCourseFee(), a.getCompletedAt(), a.getCompletionComment());
    }

    private Page<ClaimRow> rows(Page<CourseClaim> page) {
        List<CourseClaim> list = page.getContent();
        Map<Long, CourseApplication> byId = new HashMap<>();
        applications.findAllById(list.stream().map(CourseClaim::getApplicationId).toList())
                .forEach(a -> byId.put(a.getId(), a));
        Set<Long> people = new HashSet<>();
        for (CourseClaim c : list) {
            people.add(byId.get(c.getApplicationId()).getEmployeeId());
            people.add(c.getApproverId());
            if (c.getReviewedBy() != null) {
                people.add(c.getReviewedBy());
            }
        }
        Map<Long, String> names = directory.namesOf(people);
        Map<Long, String> departments = departments(byId.values());
        Map<CategoryCode, String> categoryNames = catalogue.categoryNames();
        return page.map(c -> {
            CourseApplication a = byId.get(c.getApplicationId());
            Instant last = Stream.of(c.getSubmittedAt(), c.getReviewedAt(), c.getReimbursedAt())
                    .filter(Objects::nonNull).max(Instant::compareTo).orElse(c.getSubmittedAt());
            return new ClaimRow(c.getId(), a.getId(), a.getReferenceNo(), a.getEmployeeId(),
                    names.get(a.getEmployeeId()), departments.get(a.getEmployeeId()), a.getCourseTitle(),
                    categoryNames.getOrDefault(a.getCategory(), a.getCategory().defaultLabel()), a.getCourseFee(),
                    c.getAmount(), c.getRevision(), c.getStatus(), c.getSubmittedAt(), last,
                    names.get(c.getApproverId()), c.getReviewedBy() == null ? null : names.get(c.getReviewedBy()),
                    c.getReviewComment(), c.getReviewedAt(), c.getReimbursementReference(), c.getReimbursedAt());
        });
    }

    private Map<Long, String> departments(Collection<CourseApplication> list) {
        Map<Long, String> result = new HashMap<>();
        for (CourseApplication a : list) {
            result.computeIfAbsent(a.getEmployeeId(), id -> directory.card(id).department());
        }
        return result;
    }

    private static void requireManager(Actor actor) {
        if (!actor.hasRole(Role.MANAGER)) {
            throw new NotFoundException();
        }
    }

    private static void requireAdmin(Actor actor) {
        if (!actor.hasRole(Role.ADMIN)) {
            throw new NotFoundException();
        }
    }
}
