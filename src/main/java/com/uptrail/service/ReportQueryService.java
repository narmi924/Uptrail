package com.uptrail.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uptrail.model.ApplicationStatus;
import com.uptrail.model.CourseApplication;
import com.uptrail.model.ScheduledDay;
import com.uptrail.repo.CourseApplicationRepo;
import com.uptrail.model.CategoryCode;
import com.uptrail.model.ClaimStatus;
import com.uptrail.repo.CourseFeeApplicationRepo;
import com.uptrail.model.Session;
import com.uptrail.service.EntitlementService.Balance;
import com.uptrail.model.User;
import com.uptrail.model.Role;
import com.uptrail.service.StaffService.PersonRef;
import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.error.ErrorCode;
import com.uptrail.shared.error.NotFoundException;

/**
 * Manager reports over the manager's current direct reports. The page and the CSV export call the same
 * methods, so both always show the same rows and totals. Money comes from the ledger (budget report) or
 * from each application once (participation report); nothing is summed across joins that could repeat it.
 */
@Service
@Transactional(readOnly = true)
public class ReportQueryService {

    /** Courses counted as "attended": approved (scheduled) and completed. */
    public static final Set<ApplicationStatus> ATTENDED = Set.of(ApplicationStatus.APPROVED, ApplicationStatus.COMPLETED);

    /** Money zero at the column scale, so empty totals print as 0.00 like every other amount. */
    private static final BigDecimal NONE = new BigDecimal("0.00");

    public record TrainingFilter(LocalDate from, LocalDate to, CategoryCode category, Long employeeId) {
    }

    public record TrainingRow(String referenceNo, String employeeName, String department, String courseTitle,
            CategoryCode category, String categoryName, String providerName, LocalDate startDate, Session startSession,
            LocalDate endDate, Session endSession, ApplicationStatus status, int unitsInPeriod, BigDecimal fee,
            boolean startsInPeriod) {
    }

    public record TrainingReport(TrainingFilter filter, List<TrainingRow> rows, int totalUnits,
            BigDecimal feesOfCoursesStartingInPeriod, Map<CategoryCode, Integer> unitsByCategory, long people) {
    }

    public record BudgetFilter(int year, Long employeeId) {
    }

    public record BudgetRow(String staffId, String employeeName, String department, Balance balance,
            int completedUnits, BigDecimal claimsSubmitted, BigDecimal claimsApproved, BigDecimal claimsRejected) {
    }

    public record BudgetReport(BudgetFilter filter, List<BudgetRow> rows, Balance totals, int completedUnits,
            BigDecimal claimsSubmitted, BigDecimal claimsApproved) {
    }

    private final CourseApplicationRepo applications;
    private final CourseFeeApplicationRepo claims;
    private final EntitlementService entitlements;
    private final StaffService directory;
    private final CatalogueQueryService catalogue;

    public ReportQueryService(CourseApplicationRepo applications, CourseFeeApplicationRepo claims,
            EntitlementService entitlements, StaffService directory, CatalogueQueryService catalogue) {
        this.applications = applications;
        this.claims = claims;
        this.entitlements = entitlements;
        this.directory = directory;
        this.catalogue = catalogue;
    }

    public List<PersonRef> team(User manager) {
        if (!manager.hasRole(Role.MANAGER)) {
            throw new NotFoundException();
        }
        return directory.directReports(manager.getUserId());
    }

    public TrainingReport training(User manager, TrainingFilter filter) {
        if (filter.from() == null || filter.to() == null || filter.to().isBefore(filter.from())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Choose a period whose end is on or after its start.");
        }
        List<PersonRef> people = scope(manager, filter.employeeId());
        Map<Long, PersonRef> byId = new HashMap<>();
        people.forEach(p -> byId.put(p.id(), p));
        Map<CategoryCode, String> categoryNames = catalogue.categoryNames();
        List<TrainingRow> rows = new ArrayList<>();
        Map<CategoryCode, Integer> byCategory = new EnumMap<>(CategoryCode.class);
        int totalUnits = 0;
        BigDecimal fees = NONE;
        if (!byId.isEmpty()) {
            for (CourseApplication a : applications.findForPeopleInPeriod(byId.keySet(), ATTENDED, filter.from(),
                    filter.to())) {
                if (filter.category() != null && a.getCategory() != filter.category()) {
                    continue;
                }
                int units = 0;
                for (ScheduledDay day : a.getDays()) {
                    if (!day.getTrainingDate().isBefore(filter.from()) && !day.getTrainingDate().isAfter(filter.to())) {
                        units += day.getUnits();
                    }
                }
                boolean startsInPeriod = !a.getStartDate().isBefore(filter.from()) && !a.getStartDate().isAfter(filter.to());
                PersonRef person = byId.get(a.getApplicantId());
                rows.add(new TrainingRow(a.getReferenceNo(), person.name(), person.department(), a.getCourseTitle(),
                        a.getCategory(), categoryNames.getOrDefault(a.getCategory(), a.getCategory().defaultLabel()),
                        a.getProviderName(), a.getStartDate(), a.getStartSession(), a.getEndDate(), a.getEndSession(),
                        a.getStatus(), units, a.getCourseFee(), startsInPeriod));
                totalUnits += units;
                byCategory.merge(a.getCategory(), units, Integer::sum);
                if (startsInPeriod) {
                    fees = fees.add(a.getCourseFee());
                }
            }
        }
        long attendees = rows.stream().map(TrainingRow::employeeName).distinct().count();
        return new TrainingReport(filter, rows, totalUnits, fees, byCategory, attendees);
    }

    public BudgetReport budget(User manager, BudgetFilter filter) {
        List<PersonRef> people = scope(manager, filter.employeeId());
        LocalDate from = LocalDate.of(filter.year(), 1, 1);
        LocalDate to = LocalDate.of(filter.year(), 12, 31);
        Map<Long, Map<ClaimStatus, BigDecimal>> claimTotals = new HashMap<>();
        if (!people.isEmpty()) {
            for (CourseFeeApplicationRepo.ClaimTotal total : claims.totalsByEmployee(
                    people.stream().map(PersonRef::id).toList(), from, to)) {
                claimTotals.computeIfAbsent(total.getEmployeeId(), id -> new EnumMap<>(ClaimStatus.class))
                        .put(total.getStatus(), total.getAmount());
            }
        }
        List<BudgetRow> rows = new ArrayList<>();
        int entitled = 0;
        int reserved = 0;
        int committed = 0;
        int completedTotal = 0;
        BigDecimal budget = NONE;
        BigDecimal reservedAmount = NONE;
        BigDecimal committedAmount = NONE;
        BigDecimal reimbursed = NONE;
        BigDecimal submitted = NONE;
        BigDecimal approved = NONE;
        for (PersonRef person : people) {
            Balance balance = entitlements.balance(person.id(), filter.year());
            Number completed = applications.sumCompletedUnits(person.id(), from, to);
            Map<ClaimStatus, BigDecimal> personClaims = claimTotals.getOrDefault(person.id(), Map.of());
            BigDecimal personSubmitted = personClaims.getOrDefault(ClaimStatus.SUBMITTED, NONE);
            BigDecimal personApproved = personClaims.getOrDefault(ClaimStatus.APPROVED, NONE);
            var card = directory.card(person.id());
            rows.add(new BudgetRow(card.staffId(), person.name(), person.department(), balance,
                    completed == null ? 0 : completed.intValue(), personSubmitted, personApproved,
                    personClaims.getOrDefault(ClaimStatus.REJECTED, NONE)));
            if (balance.configured()) {
                entitled += balance.entitledUnits();
                reserved += balance.reservedUnits();
                committed += balance.committedUnits();
                budget = budget.add(balance.budget());
                reservedAmount = reservedAmount.add(balance.reservedAmount());
                committedAmount = committedAmount.add(balance.committedAmount());
                reimbursed = reimbursed.add(balance.reimbursedAmount());
            }
            completedTotal += completed == null ? 0 : completed.intValue();
            submitted = submitted.add(personSubmitted);
            approved = approved.add(personApproved);
        }
        Balance totals = new Balance(filter.year(), null, true, entitled, budget, reserved, committed, reservedAmount,
                committedAmount, reimbursed);
        return new BudgetReport(filter, rows, totals, completedTotal, submitted, approved);
    }

    private List<PersonRef> scope(User manager, Long employeeId) {
        List<PersonRef> team = team(manager);
        if (employeeId == null) {
            return team;
        }
        return List.of(team.stream().filter(p -> p.id().equals(employeeId)).findFirst()
                .orElseThrow(NotFoundException::new));
    }
}
