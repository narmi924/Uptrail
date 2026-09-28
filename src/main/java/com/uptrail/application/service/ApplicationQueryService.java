package com.uptrail.application.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uptrail.application.domain.ApplicationStatus;
import com.uptrail.application.domain.CourseApplication;
import com.uptrail.application.domain.ScheduledDay;
import com.uptrail.application.repository.CourseApplicationRepository;
import com.uptrail.application.service.ApplicationViews.Actions;
import com.uptrail.application.service.ApplicationViews.DayLine;
import com.uptrail.application.service.ApplicationViews.Detail;
import com.uptrail.application.service.ApplicationViews.ExcludedLine;
import com.uptrail.application.service.ApplicationViews.LedgerLine;
import com.uptrail.application.service.ApplicationViews.Row;
import com.uptrail.application.service.ApplicationViews.TimelineEntry;
import com.uptrail.audit.domain.AggregateType;
import com.uptrail.audit.domain.AuditEvent;
import com.uptrail.audit.repository.AuditEventRepository;
import com.uptrail.catalogue.domain.CategoryCode;
import com.uptrail.catalogue.service.CatalogueQueryService;
import com.uptrail.catalogue.service.HolidayCalendarService;
import com.uptrail.claim.domain.CourseClaim;
import com.uptrail.claim.repository.CourseClaimRepository;
import com.uptrail.claim.service.ClaimPolicy;
import com.uptrail.entitlement.domain.LedgerEntry;
import com.uptrail.entitlement.domain.TrainingAccount;
import com.uptrail.entitlement.domain.TrainingDayCalculator;
import com.uptrail.entitlement.repository.TrainingAccountRepository;
import com.uptrail.entitlement.service.EntitlementService;
import com.uptrail.identity.domain.Actor;
import com.uptrail.organisation.domain.Employee;
import com.uptrail.organisation.repository.EmployeeRepository;
import com.uptrail.organisation.service.AccessScopePolicy;
import com.uptrail.organisation.service.EmployeeDirectoryService;
import com.uptrail.shared.error.NotFoundException;
import com.uptrail.shared.time.BusinessClock;

/**
 * Employee-side reads: current-year history, application detail with timeline and ledger, and the
 * dashboard. The same queries back the pages and the exports.
 */
@Service
@Transactional(readOnly = true)
public class ApplicationQueryService {

    public record Dashboard(int year, EntitlementService.Balance balance, int completedUnits, long pendingCount,
            List<Row> readyToComplete, List<Row> recent, String approverName) {
    }

    private static final Map<String, String> EVENT_LABELS = Map.ofEntries(
            Map.entry("SUBMITTED", "Submitted"), Map.entry("UPDATED", "Updated by applicant"),
            Map.entry("APPROVED", "Approved"), Map.entry("REJECTED", "Rejected"), Map.entry("DELETED", "Deleted"),
            Map.entry("CANCELLED", "Cancelled"), Map.entry("COMPLETED", "Attendance confirmed"),
            Map.entry("REASSIGNED", "Approver reassigned"));

    private final CourseApplicationRepository applications;
    private final AuditEventRepository auditEvents;
    private final EntitlementService entitlements;
    private final TrainingAccountRepository trainingAccounts;
    private final EmployeeRepository employees;
    private final EmployeeDirectoryService directory;
    private final CatalogueQueryService catalogue;
    private final HolidayCalendarService holidays;
    private final AccessScopePolicy scope;
    private final CourseClaimRepository claims;
    private final BusinessClock clock;

    public ApplicationQueryService(CourseApplicationRepository applications, AuditEventRepository auditEvents,
            EntitlementService entitlements, TrainingAccountRepository trainingAccounts, EmployeeRepository employees,
            EmployeeDirectoryService directory, CatalogueQueryService catalogue, HolidayCalendarService holidays,
            AccessScopePolicy scope, CourseClaimRepository claims, BusinessClock clock) {
        this.applications = applications;
        this.auditEvents = auditEvents;
        this.entitlements = entitlements;
        this.trainingAccounts = trainingAccounts;
        this.employees = employees;
        this.directory = directory;
        this.catalogue = catalogue;
        this.holidays = holidays;
        this.scope = scope;
        this.claims = claims;
        this.clock = clock;
    }

    /** Personal course history: only applications with training days in the given year. */
    public Page<Row> history(Long employeeId, int year, ApplicationStatus status, CategoryCode category,
            String query, Pageable pageable) {
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        Page<CourseApplication> page = applications.findForEmployeeInPeriod(employeeId, LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31), status, category, q, pageable);
        return new PageImpl<>(rows(page.getContent()), pageable, page.getTotalElements());
    }

    public Detail ownDetail(Actor actor, Long applicationId) {
        CourseApplication application = applications.findById(applicationId).orElseThrow(NotFoundException::new);
        scope.requireOwner(actor, application.getEmployeeId());
        return detail(application, ownerActions(application));
    }

    /** Detail for any read path that has already checked scope (manager review, team history). */
    public Detail detail(CourseApplication application, Actions actions) {
        Map<Long, String> names = new HashMap<>();
        List<AuditEvent> events = auditEvents.findByAggregateTypeAndAggregateKeyOrderByCreatedAtDescIdDesc(
                AggregateType.APPLICATION, application.getId().toString());
        Set<Long> people = new HashSet<>();
        people.add(application.getEmployeeId());
        people.add(application.getApproverId());
        if (application.getReviewedBy() != null) {
            people.add(application.getReviewedBy());
        }
        events.stream().map(AuditEvent::getActorEmployeeId).filter(id -> id != null).forEach(people::add);
        names.putAll(directory.namesOf(people));

        List<TimelineEntry> timeline = events.stream().map(e -> new TimelineEntry(e.getCreatedAt(),
                e.getActorEmployeeId() == null ? "System" : names.getOrDefault(e.getActorEmployeeId(), "Unknown"),
                EVENT_LABELS.getOrDefault(e.getEventType(), e.getEventType()), e.getFromState(), e.getToState(),
                e.getReason())).toList();

        List<DayLine> days = application.getDays().stream()
                .map(d -> new DayLine(d.getTrainingDate(), d.getSession(), d.getUnits())).toList();
        Employee owner = employees.findById(application.getEmployeeId()).orElseThrow();
        return new Detail(application.getId(), application.getReferenceNo(), application.getEmployeeId(),
                owner.getFullName(), owner.getDepartment(), application.getCourseTitle(), application.getCategory(),
                catalogue.categoryNames().getOrDefault(application.getCategory(),
                        application.getCategory().defaultLabel()),
                application.getCatalogueId(), application.getProviderName(), application.getStartDate(),
                application.getStartSession(), application.getEndDate(), application.getEndSession(),
                application.getCourseFee(), application.getJustification(), application.getWorkDissemination(),
                application.getStatus(), application.getSubmittedAt(), application.getUpdatedAt(),
                application.getVersion(), application.getApproverId(), names.get(application.getApproverId()),
                application.getReviewedBy() == null ? null : names.get(application.getReviewedBy()),
                application.getReviewedAt(), application.getReviewComment(), application.getCompletionComment(),
                application.getCompletedAt(), application.getCancelReason(), application.totalUnits(), days,
                excludedDates(application), timeline, ledgerLines(application.getId()), actions);
    }

    public Actions ownerActions(CourseApplication application) {
        boolean pending = application.getStatus().isPending();
        boolean approved = application.getStatus() == ApplicationStatus.APPROVED;
        boolean ended = clock.today().isAfter(application.getEndDate());
        String completeNote = approved && !ended ? "You can confirm attendance after the course ends on "
                + ApplicationCommandService.DAY.format(application.getEndDate()) + "." : null;
        Long claimId = claims.findByApplicationId(application.getId()).map(CourseClaim::getId).orElse(null);
        boolean canClaim = claimId == null && ClaimPolicy.ineligibility(application).isEmpty();
        return new Actions(pending, pending, approved, approved && ended, completeNote, canClaim, claimId);
    }

    public Dashboard dashboard(Actor actor) {
        int year = clock.currentYear();
        Long employeeId = actor.employeeId();
        EntitlementService.Balance balance = entitlements.balance(employeeId, year);
        Number completed = applications.sumCompletedUnits(employeeId, LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31));
        long pending = applications.countByEmployeeIdAndStatusIn(employeeId, ApplicationStatus.PENDING);
        List<Row> ready = rows(applications.findReadyToComplete(employeeId, clock.today()));
        List<Row> recent = rows(applications.findTop5ByEmployeeIdOrderByUpdatedAtDescIdDesc(employeeId));
        String approver = directory.approverOf(employeeId).map(directory::nameOf).orElse(null);
        return new Dashboard(year, balance, completed == null ? 0 : completed.intValue(), pending, ready, recent,
                approver);
    }

    public List<Row> rows(Collection<CourseApplication> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Set<Long> people = new HashSet<>();
        list.forEach(a -> people.add(a.getEmployeeId()));
        Map<Long, String> names = directory.namesOf(people);
        Map<CategoryCode, String> categoryNames = catalogue.categoryNames();
        List<Row> rows = new ArrayList<>();
        for (CourseApplication a : list) {
            rows.add(new Row(a.getId(), a.getReferenceNo(), a.getEmployeeId(), names.get(a.getEmployeeId()),
                    a.getCourseTitle(), a.getCategory(),
                    categoryNames.getOrDefault(a.getCategory(), a.getCategory().defaultLabel()),
                    a.getProviderName(), a.getStartDate(), a.getStartSession(), a.getEndDate(), a.getEndSession(),
                    a.totalUnits(), a.getCourseFee(), a.getStatus(), a.getUpdatedAt()));
        }
        return rows;
    }

    private List<ExcludedLine> excludedDates(CourseApplication application) {
        Set<LocalDate> counted = new HashSet<>();
        for (ScheduledDay day : application.getDays()) {
            counted.add(day.getTrainingDate());
        }
        Map<LocalDate, String> holidayNames = holidays.holidaysBetween(application.getStartDate(),
                application.getEndDate());
        List<ExcludedLine> excluded = new ArrayList<>();
        for (LocalDate date = application.getStartDate(); !date.isAfter(application.getEndDate());
                date = date.plusDays(1)) {
            if (!counted.contains(date)) {
                String reason = TrainingDayCalculator.nonWorkingReason(date, holidayNames);
                excluded.add(new ExcludedLine(date, reason == null ? "Not counted" : reason));
            }
        }
        return excluded;
    }

    private List<LedgerLine> ledgerLines(Long applicationId) {
        List<LedgerEntry> entries = entitlements.entriesForApplication(applicationId);
        Map<Long, Integer> yearOfAccount = new HashMap<>();
        for (LedgerEntry entry : entries) {
            yearOfAccount.computeIfAbsent(entry.getAccountId(), id -> trainingAccounts.findById(id)
                    .map(TrainingAccount::getCalendarYear).orElse(0));
        }
        return entries.stream().map(e -> new LedgerLine(e.getCreatedAt(), yearOfAccount.get(e.getAccountId()),
                e.getEntryType(), e.getReservedUnitsDelta(), e.getCommittedUnitsDelta(), e.getReservedAmountDelta(),
                e.getCommittedAmountDelta(), e.getReimbursedAmountDelta())).toList();
    }
}
