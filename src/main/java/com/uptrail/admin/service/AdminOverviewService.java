package com.uptrail.admin.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uptrail.catalogue.domain.TrainingCalendarYear;
import com.uptrail.catalogue.repository.PublicHolidayRepository;
import com.uptrail.catalogue.repository.TrainingCalendarYearRepository;
import com.uptrail.claim.domain.ClaimStatus;
import com.uptrail.claim.repository.CourseClaimRepository;
import com.uptrail.notification.domain.OutboxStatus;
import com.uptrail.notification.repository.OutboxMessageRepository;
import com.uptrail.organisation.repository.EmployeeRepository;
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

    private final EmployeeRepository employees;
    private final TrainingCalendarYearRepository calendarYears;
    private final PublicHolidayRepository holidays;
    private final CourseClaimRepository claims;
    private final OutboxMessageRepository outbox;
    private final BusinessClock clock;

    public AdminOverviewService(EmployeeRepository employees, TrainingCalendarYearRepository calendarYears,
            PublicHolidayRepository holidays, CourseClaimRepository claims, OutboxMessageRepository outbox,
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
