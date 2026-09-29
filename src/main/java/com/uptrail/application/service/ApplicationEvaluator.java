package com.uptrail.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uptrail.application.domain.ApplicationDetails;
import com.uptrail.application.domain.ApplicationStatus;
import com.uptrail.application.domain.CourseApplication;
import com.uptrail.application.domain.ScheduledDay;
import com.uptrail.application.repository.CourseApplicationRepository;
import com.uptrail.catalogue.domain.CategoryCode;
import com.uptrail.catalogue.service.HolidayCalendarService;
import com.uptrail.entitlement.domain.LedgerDelta;
import com.uptrail.entitlement.domain.Session;
import com.uptrail.entitlement.domain.TrainingDayCalculator;
import com.uptrail.entitlement.domain.TrainingDayCalculator.DayAllocation;
import com.uptrail.entitlement.domain.TrainingDayCalculator.ExcludedDate;
import com.uptrail.entitlement.service.EntitlementService;
import com.uptrail.entitlement.service.EntitlementService.Balance;
import com.uptrail.identity.domain.Role;
import com.uptrail.identity.repository.UserAccountRepository;
import com.uptrail.organisation.domain.Employee;
import com.uptrail.organisation.repository.EmployeeRepository;
import com.uptrail.organisation.service.EmployeeDirectoryService;
import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.error.ErrorCode;
import com.uptrail.shared.time.BusinessClock;

/**
 * Evaluates a course application against every submission rule and explains the result. The eligibility
 * preview and the real submission use this same code; the submission runs it again inside the locks, so a
 * preview never guarantees the outcome.
 */
@Service
@Transactional(readOnly = true)
public class ApplicationEvaluator {

    public static final String IN_HOUSE_PROVIDER = "In-house";
    private static final BigDecimal MAX_FEE = new BigDecimal("999999.99");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    public record Problem(ErrorCode code, String field, String message) {
    }

    public record YearAllocation(int year, boolean configured, int unitsRequested, int unitsAvailableBefore,
            int unitsAvailableAfter, BigDecimal feeRequested, BigDecimal budgetAvailableBefore,
            BigDecimal budgetAvailableAfter) {
    }

    public record Conflict(Long applicationId, String referenceNo, String courseTitle, LocalDate startDate,
            LocalDate endDate, ApplicationStatus status) {
    }

    public record Evaluation(ApplicationDetails details, List<DayAllocation> days, List<ExcludedDate> excluded,
            int totalUnits, List<YearAllocation> years, List<Conflict> conflicts, List<Problem> problems,
            Long approverId, String approverName) {

        public boolean eligible() {
            return problems.isEmpty();
        }

        public List<ScheduledDay> scheduledDays() {
            return days.stream().map(d -> new ScheduledDay(d.date(), d.session())).toList();
        }

        public SortedMap<Integer, Integer> unitsByYear() {
            SortedMap<Integer, Integer> result = new TreeMap<>();
            days.forEach(d -> result.merge(d.date().getYear(), d.units(), Integer::sum));
            return result;
        }

        /** The error raised when a submission with this evaluation is attempted. */
        public BusinessException toException() {
            Problem first = problems.get(0);
            Map<String, String> fields = new LinkedHashMap<>();
            problems.stream().filter(p -> p.field() != null).forEach(p -> fields.putIfAbsent(p.field(), p.message()));
            return new BusinessException(first.code(), first.message(), fields);
        }
    }

    private final TrainingDayCalculator calculator = new TrainingDayCalculator();
    private final HolidayCalendarService holidays;
    private final EntitlementService entitlements;
    private final CourseApplicationRepository applications;
    private final EmployeeRepository employees;
    private final UserAccountRepository accounts;
    private final EmployeeDirectoryService directory;
    private final BusinessClock clock;

    public ApplicationEvaluator(HolidayCalendarService holidays, EntitlementService entitlements,
            CourseApplicationRepository applications, EmployeeRepository employees, UserAccountRepository accounts,
            EmployeeDirectoryService directory, BusinessClock clock) {
        this.holidays = holidays;
        this.entitlements = entitlements;
        this.applications = applications;
        this.employees = employees;
        this.accounts = accounts;
        this.directory = directory;
        this.clock = clock;
    }

    /**
     * @param editedApplicationId the pending application being edited, or {@code null} for a new one; its
     *        own reservation is added back to the available balance and it is ignored for overlaps
     */
    public Evaluation evaluate(Long applicantId, ApplicationDetails input, Long editedApplicationId) {
        List<Problem> problems = new ArrayList<>();
        ApplicationDetails details = normalise(input, problems);

        List<DayAllocation> days = List.of();
        List<ExcludedDate> excluded = List.of();
        if (details.startDate() != null && details.endDate() != null && details.category() != null) {
            TrainingDayCalculator.Result schedule = schedule(details, problems);
            days = schedule.days();
            excluded = schedule.excluded();
        }

        Long approverId = null;
        String approverName = null;
        // An edit keeps the approver stored at submission; a new application uses the current routing.
        Long assigned = editedApplicationId != null
                ? applications.findById(editedApplicationId).map(CourseApplication::getApproverId).orElse(null)
                : directory.approverOf(applicantId).orElse(null);
        if (assigned == null || !isValidApprover(assigned, applicantId)) {
            problems.add(new Problem(ErrorCode.MISSING_APPROVER, null, "No approving manager is configured for you. "
                    + "Ask an administrator to set up your approval routing before applying."));
        } else {
            approverId = assigned;
            approverName = directory.nameOf(assigned);
        }

        List<Conflict> conflicts = new ArrayList<>();
        if (details.startDate() != null && details.endDate() != null
                && !details.endDate().isBefore(details.startDate())) {
            for (CourseApplication other : applications.findOverlapping(applicantId,
                    ApplicationStatus.OVERLAP_BLOCKING, details.startDate(), details.endDate(), editedApplicationId)) {
                conflicts.add(new Conflict(other.getId(), other.getReferenceNo(), other.getCourseTitle(),
                        other.getStartDate(), other.getEndDate(), other.getStatus()));
            }
            if (!conflicts.isEmpty()) {
                Conflict c = conflicts.get(0);
                problems.add(new Problem(ErrorCode.PERIOD_OVERLAP, "startDate", "This course overlaps with "
                        + c.referenceNo() + " (" + c.status().label() + ", " + DAY.format(c.startDate()) + " to "
                        + DAY.format(c.endDate()) + ")."));
            }
        }

        List<YearAllocation> years = days.isEmpty() ? List.of()
                : allocate(applicantId, details, days, editedApplicationId, problems);

        int totalUnits = days.stream().mapToInt(DayAllocation::units).sum();
        return new Evaluation(details, days, excluded, totalUnits, years, conflicts, List.copyOf(problems),
                approverId, approverName);
    }

    private ApplicationDetails normalise(ApplicationDetails input, List<Problem> problems) {
        CategoryCode category = input.category();
        if (category == null) {
            problems.add(new Problem(ErrorCode.VALIDATION_FAILED, "category", "Choose a course category."));
        }
        String title = trim(input.courseTitle());
        if (title == null) {
            problems.add(new Problem(ErrorCode.VALIDATION_FAILED, "courseTitle", "Enter the course title."));
        } else if (title.length() > 200) {
            problems.add(new Problem(ErrorCode.VALIDATION_FAILED, "courseTitle", "Use at most 200 characters."));
        }
        String provider = trim(input.providerName());
        if (provider == null && category == CategoryCode.INTERNAL) {
            provider = IN_HOUSE_PROVIDER;
        } else if (provider == null && category != null) {
            problems.add(new Problem(ErrorCode.VALIDATION_FAILED, "providerName",
                    "Enter the training provider of this external course or certification."));
        } else if (provider != null && provider.length() > 160) {
            problems.add(new Problem(ErrorCode.VALIDATION_FAILED, "providerName", "Use at most 160 characters."));
        }
        String justification = trim(input.justification());
        if (justification == null) {
            problems.add(new Problem(ErrorCode.VALIDATION_FAILED, "justification",
                    "Explain how the course benefits your work."));
        } else if (justification.length() > 2000) {
            problems.add(new Problem(ErrorCode.VALIDATION_FAILED, "justification", "Use at most 2000 characters."));
        }
        String dissemination = trim(input.workDissemination());
        if (dissemination != null && dissemination.length() > 2000) {
            problems.add(new Problem(ErrorCode.VALIDATION_FAILED, "workDissemination", "Use at most 2000 characters."));
        }

        LocalDate start = input.startDate();
        LocalDate end = input.endDate();
        LocalDate today = clock.today();
        if (start == null) {
            problems.add(new Problem(ErrorCode.VALIDATION_FAILED, "startDate", "Enter the start date."));
        } else if (!start.isAfter(today)) {
            problems.add(new Problem(ErrorCode.VALIDATION_FAILED, "startDate",
                    "The course must start on a future date (after " + DAY.format(today) + ")."));
        }
        if (end == null) {
            problems.add(new Problem(ErrorCode.VALIDATION_FAILED, "endDate", "Enter the end date."));
        }
        if (start != null && end != null) {
            int firstYear = clock.currentYear();
            if (start.getYear() < firstYear || end.getYear() > firstYear + 1) {
                problems.add(new Problem(ErrorCode.YEAR_NOT_OPEN, "endDate", "Applications can only cover "
                        + firstYear + " and " + (firstYear + 1) + "."));
            }
        }

        Session startSession = input.startSession() == null ? Session.AM : input.startSession();
        Session endSession = input.endSession() == null ? Session.PM : input.endSession();

        BigDecimal fee = input.courseFee();
        if (category == CategoryCode.INTERNAL) {
            if (fee != null && fee.signum() != 0) {
                problems.add(new Problem(ErrorCode.VALIDATION_FAILED, "courseFee",
                        "Internal training carries no course fee."));
            }
            fee = BigDecimal.ZERO;
        } else if (category != null) {
            if (fee == null || fee.signum() <= 0) {
                problems.add(new Problem(ErrorCode.VALIDATION_FAILED, "courseFee",
                        "Enter the course fee; external courses and certifications are fee-paying."));
            } else if (fee.scale() > 2 && fee.stripTrailingZeros().scale() > 2) {
                problems.add(new Problem(ErrorCode.VALIDATION_FAILED, "courseFee", "Use at most two decimal places."));
            } else if (fee.compareTo(MAX_FEE) > 0) {
                problems.add(new Problem(ErrorCode.VALIDATION_FAILED, "courseFee", "The fee is too large."));
            }
        }
        BigDecimal normalisedFee = fee == null ? null : fee.setScale(2, RoundingMode.HALF_UP);
        return new ApplicationDetails(category, input.catalogueId(), title, provider, start, end, startSession,
                endSession, normalisedFee, justification, dissemination);
    }

    private TrainingDayCalculator.Result schedule(ApplicationDetails details, List<Problem> problems) {
        LocalDate start = details.startDate();
        LocalDate end = details.endDate();
        if (end.isBefore(start)) {
            problems.add(new Problem(ErrorCode.VALIDATION_FAILED, "endDate",
                    "The end date must be on or after the start date."));
            return calculator.calculate(new TrainingDayCalculator.Request(start, end, details.startSession(),
                    details.endSession(), details.category().isHalfDayAllowed()), Map.of());
        }
        boolean calendarsReady = true;
        for (int year = start.getYear(); year <= end.getYear(); year++) {
            if (!holidays.isConfirmed(year)) {
                calendarsReady = false;
                problems.add(new Problem(ErrorCode.HOLIDAY_CALENDAR_UNCONFIRMED, null, "The public holiday calendar "
                        + "for " + year + " has not been confirmed by an administrator yet, so training days "
                        + "cannot be counted."));
            }
        }
        TrainingDayCalculator.Result result = calculator.calculate(new TrainingDayCalculator.Request(start, end,
                details.startSession(), details.endSession(), details.category().isHalfDayAllowed()),
                calendarsReady ? holidays.holidaysBetween(start, end) : Map.of());
        if (!calendarsReady) {
            return new TrainingDayCalculator.Result(List.of(), List.of(), result.problems(), null, null);
        }
        for (TrainingDayCalculator.Problem problem : result.problems()) {
            switch (problem) {
                case DATE_ORDER -> problems.add(new Problem(ErrorCode.VALIDATION_FAILED, "endDate",
                        "The end date must be on or after the start date."));
                case PERIOD_TOO_LONG -> problems.add(new Problem(ErrorCode.VALIDATION_FAILED, "endDate",
                        "A course period cannot be longer than one year."));
                case START_NOT_WORKING_DAY -> problems.add(new Problem(ErrorCode.NON_WORKING_BOUNDARY, "startDate",
                        "The start date " + DAY.format(start) + " is not a working day (" + result.startReason()
                                + ")."));
                case END_NOT_WORKING_DAY -> problems.add(new Problem(ErrorCode.NON_WORKING_BOUNDARY, "endDate",
                        "The end date " + DAY.format(end) + " is not a working day (" + result.endReason() + ")."));
                case HALF_DAY_NOT_ALLOWED -> problems.add(new Problem(ErrorCode.VALIDATION_FAILED, "startSession",
                        "Half-day sessions are allowed for internal training only; this course must run from the "
                                + "morning of the first day to the afternoon of the last day."));
                case SESSION_ORDER -> problems.add(new Problem(ErrorCode.VALIDATION_FAILED, "endSession",
                        "A single-day course cannot start in the afternoon and end in the morning."));
            }
        }
        return result;
    }

    private List<YearAllocation> allocate(Long applicantId, ApplicationDetails details, List<DayAllocation> days,
            Long editedApplicationId, List<Problem> problems) {
        SortedMap<Integer, Integer> unitsByYear = new TreeMap<>();
        days.forEach(d -> unitsByYear.merge(d.date().getYear(), d.units(), Integer::sum));
        int feeYear = details.startDate().getYear();
        unitsByYear.putIfAbsent(feeYear, 0);

        List<YearAllocation> result = new ArrayList<>();
        List<Integer> missing = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : unitsByYear.entrySet()) {
            int year = entry.getKey();
            int units = entry.getValue();
            BigDecimal fee = year == feeYear && details.courseFee() != null ? details.courseFee() : BigDecimal.ZERO;
            fee = fee.setScale(2, RoundingMode.HALF_UP);
            Balance balance = entitlements.balance(applicantId, year);
            if (!balance.configured()) {
                missing.add(year);
                result.add(new YearAllocation(year, false, units, 0, 0, fee, BigDecimal.ZERO.setScale(2),
                        BigDecimal.ZERO.setScale(2)));
                continue;
            }
            int unitsBefore = balance.availableUnits();
            BigDecimal budgetBefore = balance.availableBudget();
            if (editedApplicationId != null) {
                // An edit replaces the application's own reservation, so it must not count against itself.
                LedgerDelta own = entitlements.contribution(balance.accountId(), editedApplicationId);
                unitsBefore += own.reservedUnits() + own.committedUnits();
                budgetBefore = budgetBefore.add(own.reservedAmount()).add(own.committedAmount());
            }
            YearAllocation allocation = new YearAllocation(year, true, units, unitsBefore, unitsBefore - units, fee,
                    budgetBefore, budgetBefore.subtract(fee));
            result.add(allocation);
            if (units > unitsBefore) {
                problems.add(new Problem(ErrorCode.INSUFFICIENT_DAYS, "endDate", formatDays(units)
                        + " of training in " + year + " are required; " + formatDays(Math.max(unitsBefore, 0))
                        + " remain."));
            }
            if (fee.compareTo(budgetBefore) > 0) {
                problems.add(new Problem(ErrorCode.INSUFFICIENT_BUDGET, "courseFee", "SGD " + fee.toPlainString()
                        + " is required; SGD " + budgetBefore.max(BigDecimal.ZERO).setScale(2).toPlainString()
                        + " remains in the " + year + " training budget."));
            }
        }
        if (!missing.isEmpty()) {
            BusinessException error = EntitlementService.missingAccount(missing);
            problems.add(new Problem(ErrorCode.MISSING_ANNUAL_ACCOUNT, null, error.getMessage()));
        }
        return result;
    }

    /**
     * Recalculates a submitted schedule with today's holiday calendar. Returns a message when the result
     * differs from the stored snapshot, i.e. when the holiday calendar changed after submission.
     */
    public Optional<String> scheduleChange(CourseApplication application) {
        for (int year = application.getStartDate().getYear(); year <= application.getEndDate().getYear(); year++) {
            if (!holidays.isConfirmed(year)) {
                return Optional.of("The public holiday calendar for " + year + " is not confirmed at the moment, "
                        + "so this application cannot be decided yet.");
            }
        }
        TrainingDayCalculator.Result current = calculator.calculate(new TrainingDayCalculator.Request(
                application.getStartDate(), application.getEndDate(), application.getStartSession(),
                application.getEndSession(), application.getCategory().isHalfDayAllowed()),
                holidays.holidaysBetween(application.getStartDate(), application.getEndDate()));
        List<ScheduledDay> recalculated = current.days().stream()
                .map(d -> new ScheduledDay(d.date(), d.session())).toList();
        if (!current.valid() || !recalculated.equals(application.getDays())) {
            return Optional.of("The public holiday calendar changed after this application was submitted: it now "
                    + "counts " + formatDays(current.totalUnits()) + " instead of " + formatDays(application.totalUnits())
                    + (current.valid() ? "" : " and a boundary date is no longer a working day")
                    + ". Ask the applicant to update the application before deciding.");
        }
        return Optional.empty();
    }

    private boolean isValidApprover(Long approverId, Long applicantId) {
        if (approverId.equals(applicantId)) {
            return false;
        }
        Employee approver = employees.findById(approverId).orElse(null);
        return approver != null && approver.isActive() && accounts.employeeHasRole(approverId, Role.MANAGER);
    }

    public static String formatDays(int units) {
        String days = units % 2 == 0 ? String.valueOf(units / 2) : (units / 2) + ".5";
        return days + (units == 2 ? " day" : " days");
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
