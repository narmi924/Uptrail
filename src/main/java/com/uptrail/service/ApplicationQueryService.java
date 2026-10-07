package com.uptrail.service;

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

import com.uptrail.model.ApplicationStatus;
import com.uptrail.model.CourseApplication;
import com.uptrail.model.ScheduledDay;
import com.uptrail.repo.CourseApplicationRepo;
import com.uptrail.service.ApplicationViews.Actions;
import com.uptrail.service.ApplicationViews.DayLine;
import com.uptrail.service.ApplicationViews.Detail;
import com.uptrail.service.ApplicationViews.ExcludedLine;
import com.uptrail.service.ApplicationViews.LedgerLine;
import com.uptrail.service.ApplicationViews.Row;
import com.uptrail.service.ApplicationViews.TimelineEntry;
import com.uptrail.model.AggregateType;
import com.uptrail.model.AuditEvent;
import com.uptrail.repo.AuditEventRepo;
import com.uptrail.model.CategoryCode;
import com.uptrail.model.CourseFeeApplication;
import com.uptrail.repo.CourseFeeApplicationRepo;
import com.uptrail.model.LedgerEntry;
import com.uptrail.model.TrainingEntitlement;
import com.uptrail.model.TrainingDayCalculator;
import com.uptrail.repo.TrainingEntitlementRepo;
import com.uptrail.model.User;
import com.uptrail.repo.UserRepo;
import com.uptrail.shared.error.NotFoundException;
import com.uptrail.shared.time.BusinessClock;

/**
 * User-side reads: current-year history, application detail with timeline and ledger, and the
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

    private final CourseApplicationRepo applications;
    private final AuditEventRepo auditEvents;
    private final EntitlementService entitlements;
    private final TrainingEntitlementRepo trainingAccounts;
    private final UserRepo employees;
    private final StaffService directory;
    private final CatalogueQueryService catalogue;
    private final HolidayCalendarService holidays;
    private final AccessScopePolicy scope;
    private final CourseFeeApplicationRepo claims;
    private final BusinessClock clock;

    public ApplicationQueryService(CourseApplicationRepo applications, AuditEventRepo auditEvents,
            EntitlementService entitlements, TrainingEntitlementRepo trainingAccounts, UserRepo employees,
            StaffService directory, CatalogueQueryService catalogue, HolidayCalendarService holidays,
            AccessScopePolicy scope, CourseFeeApplicationRepo claims, BusinessClock clock) {
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

    public Detail ownDetail(User actor, Long applicationId) {
        CourseApplication application = applications.findById(applicationId).orElseThrow(NotFoundException::new);
        scope.requireOwner(actor, application.getApplicantId());
        return detail(application, ownerActions(application));
    }

    /** Detail for any read path that has already checked scope (manager review, team history). */
    public Detail detail(CourseApplication application, Actions actions) {
        Map<Long, String> names = new HashMap<>();
        List<AuditEvent> events = auditEvents.findByAggregateTypeAndAggregateKeyOrderByCreatedAtDescIdDesc(
                AggregateType.APPLICATION, application.getId().toString());
        Set<Long> people = new HashSet<>();
        people.add(application.getApplicantId());
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
        User owner = employees.findById(application.getApplicantId()).orElseThrow();
        return new Detail(application.getId(), application.getReferenceNo(), application.getApplicantId(),
                owner.getName(), owner.getDepartment(), application.getCourseTitle(), application.getCategory(),
                catalogue.categoryNames().getOrDefault(application.getCategory(),
                        application.getCategory().defaultLabel()),
                application.getCatalogueId(), application.getProviderName(), application.getStartDate(),
                application.getStartSession(), application.getEndDate(), application.getEndSession(),
                application.getCourseFee(), application.getJustification(), application.getWorkDissemination(),
                application.getStatus(), application.getSubmittedAt(), application.getUpdatedAt(),
                application.getVersion(), application.getApproverId(), names.get(application.getApproverId()),
                application.getReviewedBy() == null ? null : names.get(application.getReviewedBy()),
                application.getReviewedAt(), application.getDecisionReason(), application.getCompletionComment(),
                application.getCompletedAt(), application.getCancelReason(), application.totalUnits(), days,
                excludedDates(application), timeline, ledgerLines(application.getId()), actions);
    }

    public Actions ownerActions(CourseApplication application) {
        boolean pending = application.getStatus().isPending();
        boolean approved = application.getStatus() == ApplicationStatus.APPROVED;
        boolean ended = clock.today().isAfter(application.getEndDate());
        String completeNote = approved && !ended ? "You can confirm attendance after the course ends on "
                + CourseApplicationService.DAY.format(application.getEndDate()) + "." : null;
        Long claimId = claims.findByApplicationId(application.getId()).map(CourseFeeApplication::getId).orElse(null);
        boolean canClaim = claimId == null && ClaimPolicy.ineligibility(application).isEmpty();
        return new Actions(pending, pending, approved, approved && ended, completeNote, canClaim, claimId);
    }

    public Dashboard dashboard(User actor) {
        int year = clock.currentYear();
        Long employeeId = actor.getUserId();
        EntitlementService.Balance balance = entitlements.balance(employeeId, year);
        Number completed = applications.sumCompletedUnits(employeeId, LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31));
        long pending = applications.countByApplicantIdAndStatusIn(employeeId, ApplicationStatus.PENDING);
        List<Row> ready = rows(applications.findReadyToComplete(employeeId, clock.today()));
        List<Row> recent = rows(applications.findTop5ByApplicantIdOrderByUpdatedAtDescIdDesc(employeeId));
        String approver = directory.approverOf(employeeId).map(directory::nameOf).orElse(null);
        return new Dashboard(year, balance, completed == null ? 0 : completed.intValue(), pending, ready, recent,
                approver);
    }

    public List<Row> rows(Collection<CourseApplication> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Set<Long> people = new HashSet<>();
        list.forEach(a -> people.add(a.getApplicantId()));
        Map<Long, String> names = directory.namesOf(people);
        Map<CategoryCode, String> categoryNames = catalogue.categoryNames();
        List<Row> rows = new ArrayList<>();
        for (CourseApplication a : list) {
            rows.add(new Row(a.getId(), a.getReferenceNo(), a.getApplicantId(), names.get(a.getApplicantId()),
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
                    .map(TrainingEntitlement::getCalendarYear).orElse(0));
        }
        return entries.stream().map(e -> new LedgerLine(e.getCreatedAt(), yearOfAccount.get(e.getAccountId()),
                e.getEntryType(), e.getReservedUnitsDelta(), e.getCommittedUnitsDelta(), e.getReservedAmountDelta(),
                e.getCommittedAmountDelta(), e.getReimbursedAmountDelta())).toList();
    }
}
