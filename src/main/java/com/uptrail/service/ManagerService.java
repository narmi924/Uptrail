package com.uptrail.service;

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

import com.uptrail.model.ApplicationStatus;
import com.uptrail.model.CourseApplication;
import com.uptrail.model.ScheduledDay;
import com.uptrail.repo.CourseApplicationRepo;
import com.uptrail.service.ApplicationViews.Actions;
import com.uptrail.service.ApplicationViews.Detail;
import com.uptrail.service.ApplicationViews.Row;
import com.uptrail.model.User;
import com.uptrail.model.Manager;
import com.uptrail.repo.ManagerRepo;
import com.uptrail.shared.tx.WriteTransaction;
import com.uptrail.model.Role;
import com.uptrail.service.StaffService.PersonRef;
import com.uptrail.shared.error.NotFoundException;
import com.uptrail.shared.time.BusinessClock;
import com.uptrail.shared.web.Paging;

/**
 * Manager reads: the approval worklist grouped by subordinate, the review page and subordinate history.
 * Every read is limited to applications the manager approves, decided, or whose owner reports to them.
 */
@Service
@Transactional(readOnly = true)
public class ManagerService {

    private static final Set<ApplicationStatus> APPROVED_OR_DONE = Set.of(ApplicationStatus.APPROVED,
            ApplicationStatus.COMPLETED);

    public record Group(Long employeeId, String employeeName, String department, List<Row> applications) {
    }

    public record YearUsage(int year, EntitlementService.Balance balance, int completedUnits) {
    }

    public record Review(Detail application, List<YearUsage> usage, List<Row> teamInPeriod, boolean canDecide,
            String decisionNote, String scheduleWarning) {
    }

    private final CourseApplicationRepo applications;
    private final ApplicationQueryService queries;
    private final ApplicationEvaluator evaluator;
    private final EntitlementService entitlements;
    private final StaffService directory;
    private final AccessScopePolicy scope;
    private final BusinessClock clock;
    private final ManagerRepo managers;
    private final CourseApplicationService commands;

    public ManagerService(CourseApplicationRepo applications, ApplicationQueryService queries,
            ApplicationEvaluator evaluator, EntitlementService entitlements, StaffService directory,
            AccessScopePolicy scope, BusinessClock clock, ManagerRepo managers, CourseApplicationService commands) {
        this.applications = applications;
        this.queries = queries;
        this.evaluator = evaluator;
        this.entitlements = entitlements;
        this.directory = directory;
        this.scope = scope;
        this.clock = clock;
        this.managers = managers;
        this.commands = commands;
    }

    @Transactional(readOnly = true)
    public Manager getByStaffId(String staffId) {
        return managers.findByStaffId(staffId).orElseThrow(com.uptrail.shared.error.NotFoundException::new);
    }

    @WriteTransaction
    public void approveCourseApplication(Manager manager, Long id, String reason, Long expectedVersion) {
        commands.decide(manager, id, CourseApplicationService.Decision.APPROVE, reason, expectedVersion);
    }

    @WriteTransaction
    public void rejectCourseApplication(Manager manager, Long id, String reason, Long expectedVersion) {
        commands.decide(manager, id, CourseApplicationService.Decision.REJECT, reason, expectedVersion);
    }

    /**
     * Pending applications assigned to the manager, grouped by applicant and ordered by name. Pages contain
     * whole groups, so one subordinate's applications are never split across pages.
     */
    public Page<Group> pendingGroups(User manager, Pageable pageable) {
        requireManager(manager);
        List<CourseApplication> pending = applications.findByApproverIdAndStatusInOrderByStartDateAscIdAsc(
                manager.getUserId(), ApplicationStatus.PENDING);
        Map<Long, List<Row>> byEmployee = new LinkedHashMap<>();
        for (Row row : queries.rows(pending)) {
            byEmployee.computeIfAbsent(row.employeeId(), id -> new ArrayList<>()).add(row);
        }
        Map<Long, PersonRef> people = new LinkedHashMap<>();
        for (Long id : byEmployee.keySet()) {
            var card = directory.card(id);
            people.put(id, new PersonRef(id, card.name(), card.department(), card.active()));
        }
        List<Group> groups = byEmployee.entrySet().stream()
                .map(e -> new Group(e.getKey(), people.get(e.getKey()).name(), people.get(e.getKey()).department(),
                        e.getValue()))
                .sorted(Comparator.comparing(Group::employeeName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Group::employeeId))
                .toList();
        return Paging.slice(groups, pageable);
    }

    public long pendingCount(User manager) {
        return applications.countByApproverIdAndStatusIn(manager.getUserId(), ApplicationStatus.PENDING);
    }

    public Review review(User manager, Long applicationId) {
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
            Number completed = applications.sumCompletedUnits(application.getApplicantId(), LocalDate.of(year, 1, 1),
                    LocalDate.of(year, 12, 31));
            usage.add(new YearUsage(year, entitlements.balance(application.getApplicantId(), year),
                    completed == null ? 0 : completed.intValue()));
        }

        List<Long> otherReports = directory.directReports(manager.getUserId()).stream().map(PersonRef::id)
                .filter(id -> !id.equals(application.getApplicantId())).toList();
        List<Row> team = otherReports.isEmpty() ? List.of() : queries.rows(applications.findForPeopleInPeriod(
                otherReports, APPROVED_OR_DONE, application.getStartDate(), application.getEndDate()));

        boolean pending = application.getStatus().isPending();
        boolean assigned = Objects.equals(application.getApproverId(), manager.getUserId());
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

    public List<PersonRef> directReports(User manager) {
        requireManager(manager);
        return directory.directReports(manager.getUserId());
    }

    /** Subordinate course history, like the personal history: applications with training days in the year. */
    public Page<Row> teamHistory(User manager, Long employeeId, int year, ApplicationStatus status,
            Pageable pageable) {
        requireManager(manager);
        scope.requireCurrentDirectManager(manager, employeeId);
        return queries.history(employeeId, year, status, null, null, pageable);
    }

    public Detail teamDetail(User manager, Long applicationId) {
        requireManager(manager);
        CourseApplication application = visibleApplication(manager, applicationId);
        return queries.detail(application, new Actions(false, false, false, false, null, false, null));
    }

    private CourseApplication visibleApplication(User manager, Long applicationId) {
        CourseApplication application = applications.findById(applicationId).orElseThrow(NotFoundException::new);
        if (!scope.canManagerView(manager, application.getApplicantId(), application.getApproverId(),
                application.getReviewedBy())) {
            throw new NotFoundException();
        }
        return application;
    }

    private static void requireManager(User actor) {
        if (!actor.hasRole(Role.MANAGER)) {
            throw new NotFoundException();
        }
    }
}
