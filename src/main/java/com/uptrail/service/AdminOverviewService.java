package com.uptrail.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uptrail.model.TrainingCalendarYear;
import com.uptrail.repo.ExcludedDaysRepo;
import com.uptrail.repo.TrainingCalendarYearRepo;
import com.uptrail.model.ClaimStatus;
import com.uptrail.repo.CourseFeeApplicationRepo;
import com.uptrail.model.OutboxStatus;
import com.uptrail.repo.OutboxMessageRepo;
import com.uptrail.repo.UserRepo;
import com.uptrail.shared.time.BusinessClock;

/**
 * Counts shown on the administration dashboard. Every number comes from a database query.
 */
@Service
@Transactional(readOnly = true)
public class AdminOverviewService {

    public record YearReadiness(int year, String calendarStatus, long holidayCount, long applicantsWithoutAccount) {
    }

    public record Overview(long activeStaff, long inactiveStaff, long applicantsWithoutApprover,
            List<YearReadiness> years, long claimsAwaitingReimbursement, long failedEmails) {
    }

    private final UserRepo employees;
    private final TrainingCalendarYearRepo calendarYears;
    private final ExcludedDaysRepo holidays;
    private final CourseFeeApplicationRepo claims;
    private final OutboxMessageRepo outbox;
    private final BusinessClock clock;

    public AdminOverviewService(UserRepo employees, TrainingCalendarYearRepo calendarYears,
            ExcludedDaysRepo holidays, CourseFeeApplicationRepo claims, OutboxMessageRepo outbox,
            BusinessClock clock) {
        this.employees = employees;
        this.calendarYears = calendarYears;
        this.holidays = holidays;
        this.claims = claims;
        this.outbox = outbox;
        this.clock = clock;
    }

    public Overview overview() {
        int currentYear = clock.currentYear();
        List<YearReadiness> years = new ArrayList<>();
        for (int year = currentYear; year <= currentYear + 1; year++) {
            String status = calendarYears.findById(year).map(TrainingCalendarYear::getStatus).map(Enum::name)
                    .orElse("NOT_CONFIGURED");
            long holidayCount = holidays.countByHolidayDateBetween(LocalDate.of(year, 1, 1),
                    LocalDate.of(year, 12, 31));
            years.add(new YearReadiness(year, status, holidayCount,
                    employees.countActiveApplicantsWithoutAccount(year)));
        }
        return new Overview(employees.countByActive(true), employees.countByActive(false),
                employees.countActiveApplicantsWithoutApprover(), years,
                claims.countByStatus(ClaimStatus.APPROVED), outbox.countByStatus(OutboxStatus.FAILED));
    }
}
