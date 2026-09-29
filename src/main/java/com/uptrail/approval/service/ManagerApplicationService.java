package com.uptrail.approval.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uptrail.application.domain.ApplicationStatus;
import com.uptrail.application.domain.CourseApplication;
import com.uptrail.application.domain.ScheduledDay;
import com.uptrail.application.repository.CourseApplicationRepository;
import com.uptrail.application.service.ApplicationEvaluator;
import com.uptrail.application.service.ApplicationQueryService;
import com.uptrail.application.service.ApplicationViews.Actions;
import com.uptrail.application.service.ApplicationViews.Detail;
import com.uptrail.application.service.ApplicationViews.Row;
import com.uptrail.entitlement.service.EntitlementService;
import com.uptrail.identity.domain.Actor;
import com.uptrail.identity.domain.Role;
import com.uptrail.organisation.service.AccessScopePolicy;
import com.uptrail.organisation.service.EmployeeDirectoryService;
import com.uptrail.organisation.service.EmployeeDirectoryService.PersonRef;
import com.uptrail.shared.error.NotFoundException;
import com.uptrail.shared.time.BusinessClock;
import com.uptrail.shared.web.Paging;

/**
 * Manager reads: the approval worklist grouped by subordinate, the review page and subordinate history.
 * Every read is limited to applications the manager approves, decided, or whose owner reports to them.
 */
@Service
@Transactional(readOnly = true)
public class ManagerApplicationService {

    private static final Set<ApplicationStatus> APPROVED_OR_DONE = Set.of(ApplicationStatus.APPROVED,
            ApplicationStatus.COMPLETED);

    public record Group(Long employeeId, String employeeName, String department, List<Row> applications) {
    }

    public record YearUsage(int year, EntitlementService.Balance balance, int completedUnits) {
    }

    public record Review(Detail application, List<YearUsage> usage, List<Row> teamInPeriod, boolean canDecide,
            String decisionNote, String scheduleWarning) {
    }

    private final CourseApplicationRepository applications;
    private final ApplicationQueryService queries;
    private final ApplicationEvaluator evaluator;
    private final EntitlementService entitlements;
    private final EmployeeDirectoryService directory;
    private final AccessScopePolicy scope;
    private final BusinessClock clock;

    public ManagerApplicationService(CourseApplicationRepository applications, ApplicationQueryService queries,
            ApplicationEvaluator evaluator, EntitlementService entitlements, EmployeeDirectoryService directory,
            AccessScopePolicy scope, BusinessClock clock) {
        this.applications = applications;
        this.queries = queries;
        this.evaluator = evaluator;
        this.entitlements = entitlements;
        this.directory = directory;
        this.scope = scope;
        this.clock = clock;
    }

    /**
     * Pending applications assigned to the manager, grouped by applicant and ordered by name. Pages contain
     * whole groups, so one subordinate's applications are never split across pages.
     */
    public Page<Group> pendingGroups(Actor manager, Pageable pageable) {
        requireManager(manager);
        List<CourseApplication> pending = applications.findByApproverIdAndStatusInOrderByStartDateAscIdAsc(
                manager.employeeId(), ApplicationStatus.PENDING);
        Map<Long, List<Row>> byEmployee = new LinkedHashMap<>();
        for (Row row : queries.rows(pending)) {
            byEmployee.computeIfAbsent(row.employeeId(), id -> new ArrayList<>()).add(row);
        }
        Map<Long, PersonRef> people = new LinkedHashMap<>();
        for (Long id : byEmployee.keySet()) {
            var card = directory.card(id);
            people.put(id, new PersonRef(id, card.fullName(), card.department(), card.active()));
        }
        List<Group> groups = byEmployee.entrySet().stream()
                .map(e -> new Group(e.getKey(), people.get(e.getKey()).fullName(), people.get(e.getKey()).department(),
                        e.getValue()))
                .sorted(Comparator.comparing(Group::employeeName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Group::employeeId))
                .toList();
        return Paging.slice(groups, pageable);
    }

    public long pendingCount(Actor manager) {
        return applications.countByApproverIdAndStatusIn(manager.employeeId(), ApplicationStatus.PENDING);
    }

    public Review review(Actor manager, Long applicationId) {
        requireManager(manager);
        CourseApplication application = visibleApplication(manager, applicationId);
        Detail detail = queries.detail(application, new Actions(false, false, false, false, null, false, null));

        Set<Integer> years = new TreeSet<>();
        years.add(clock.currentYear());
        for (ScheduledDay day : application.getDays()) {
            years.add(day.getTrainingDate().getYear());
        }
        List<YearUsage> usage = new ArrayList<>();
        for (int year : years) {
            Number completed = applications.sumCompletedUnits(application.getEmployeeId(), LocalDate.of(year, 1, 1),
                    LocalDate.of(year, 12, 31));
            usage.add(new YearUsage(year, entitlements.balance(application.getEmployeeId(), year),
                    completed == null ? 0 : completed.intValue()));
        }

        List<Long> otherReports = directory.directReports(manager.employeeId()).stream().map(PersonRef::id)
                .filter(id -> !id.equals(application.getEmployeeId())).toList();
        List<Row> team = otherReports.isEmpty() ? List.of() : queries.rows(applications.findForPeopleInPeriod(
                otherReports, APPROVED_OR_DONE, application.getStartDate(), application.getEndDate()));

        boolean pending = application.getStatus().isPending();
        boolean assigned = Objects.equals(application.getApproverId(), manager.employeeId());
        boolean canDecide = pending && assigned;
        String note = null;
        if (!pending) {
            note = "This application is " + application.getStatus().label().toLowerCase() + "; no decision is needed.";
        } else if (!assigned) {
            note = "This application is assigned to " + directory.nameOf(application.getApproverId()) + ".";
        }
        String warning = pending ? evaluator.scheduleChange(application).orElse(null) : null;
        return new Review(detail, usage, team, canDecide, note, warning);
    }

    public List<PersonRef> directReports(Actor manager) {
        requireManager(manager);
        return directory.directReports(manager.employeeId());
    }

    /** Subordinate course history, like the personal history: applications with training days in the year. */
    public Page<Row> teamHistory(Actor manager, Long employeeId, int year, ApplicationStatus status,
            Pageable pageable) {
        requireManager(manager);
        scope.requireCurrentDirectManager(manager, employeeId);
        return queries.history(employeeId, year, status, null, null, pageable);
    }

    public Detail teamDetail(Actor manager, Long applicationId) {
        requireManager(manager);
        CourseApplication application = visibleApplication(manager, applicationId);
        return queries.detail(application, new Actions(false, false, false, false, null, false, null));
    }

    private CourseApplication visibleApplication(Actor manager, Long applicationId) {
        CourseApplication application = applications.findById(applicationId).orElseThrow(NotFoundException::new);
        if (!scope.canManagerView(manager, application.getEmployeeId(), application.getApproverId(),
                application.getReviewedBy())) {
            throw new NotFoundException();
        }
        return application;
    }

    private static void requireManager(Actor actor) {
        if (!actor.hasRole(Role.MANAGER)) {
            throw new NotFoundException();
        }
    }
}
