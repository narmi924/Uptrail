package com.uptrail.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uptrail.model.ApplicationStatus;
import com.uptrail.model.CourseApplication;
import com.uptrail.repo.CourseApplicationRepo;
import com.uptrail.model.AggregateType;
import com.uptrail.model.CalendarYearStatus;
import com.uptrail.model.ExcludedDays;
import com.uptrail.model.TrainingCalendarYear;
import com.uptrail.repo.ExcludedDaysRepo;
import com.uptrail.repo.TrainingCalendarYearRepo;
import com.uptrail.model.User;
import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.error.ErrorCode;
import com.uptrail.shared.error.NotFoundException;
import com.uptrail.shared.time.BusinessClock;
import com.uptrail.shared.tx.WriteTransaction;

/**
 * Public holiday calendar maintenance. Adding or removing a holiday date of a confirmed year sets the year
 * back to DRAFT: working days of that year cannot be counted until an administrator confirms it again.
 * Existing applications keep their schedule snapshot; pending ones are re-checked at approval and
 * approved ones are listed so the administrator can follow up.
 */
@Service
public class HolidayAdminService {

    private static final Set<ApplicationStatus> AFFECTED = EnumSet.of(ApplicationStatus.APPLIED,
            ApplicationStatus.UPDATED, ApplicationStatus.APPROVED);

    public record HolidayRow(LocalDate date, String name, String sourceNote) {
    }

    public record YearView(int year, boolean exists, CalendarYearStatus status, String sourceNote,
            String confirmedByName, Instant confirmedAt, Integer confirmedHolidayCount, List<HolidayRow> holidays,
            int bundledHolidays) {
    }

    public record ImpactRow(Long applicationId, String referenceNo, String employeeName, String courseTitle,
            ApplicationStatus status, LocalDate startDate, LocalDate endDate) {
    }

    public record Impact(LocalDate date, boolean yearConfirmed, List<ImpactRow> applications) {

        public boolean needsConfirmation() {
            return yearConfirmed || !applications.isEmpty();
        }
    }

    private final TrainingCalendarYearRepo years;
    private final ExcludedDaysRepo holidays;
    private final CourseApplicationRepo applications;
    private final OfficialHolidayData official;
    private final StaffService directory;
    private final AuditService audit;
    private final BusinessClock clock;

    public HolidayAdminService(TrainingCalendarYearRepo years, ExcludedDaysRepo holidays,
            CourseApplicationRepo applications, OfficialHolidayData official, StaffService directory,
            AuditService audit, BusinessClock clock) {
        this.years = years;
        this.holidays = holidays;
        this.applications = applications;
        this.official = official;
        this.directory = directory;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Integer> selectableYears() {
        Set<Integer> result = new TreeSet<>();
        years.findAllByOrderByCalendarYearAsc().forEach(y -> result.add(y.getCalendarYear()));
        result.add(clock.currentYear());
        result.add(clock.currentYear() + 1);
        return List.copyOf(result);
    }

    @Transactional(readOnly = true)
    public YearView year(int year) {
        List<HolidayRow> rows = holidays.findByHolidayDateBetweenOrderByHolidayDate(LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31)).stream()
                .map(h -> new HolidayRow(h.getHolidayDate(), h.getName(), h.getSourceNote())).toList();
        int bundled = official.forYear(year).size();
        return years.findById(year).map(y -> new YearView(year, true, y.getStatus(), y.getSourceNote(),
                y.getConfirmedBy() == null ? null : directory.nameOf(y.getConfirmedBy()), y.getConfirmedAt(),
                y.getConfirmedHolidayCount(), rows, bundled))
                .orElse(new YearView(year, false, null, null, null, null, null, rows, bundled));
    }

    @Transactional(readOnly = true)
    public Impact impact(LocalDate date) {
        boolean confirmed = years.findById(date.getYear()).map(TrainingCalendarYear::isConfirmed).orElse(false);
        List<ImpactRow> rows = new ArrayList<>();
        for (CourseApplication a : applications.findActiveOn(date, AFFECTED)) {
            rows.add(new ImpactRow(a.getId(), a.getReferenceNo(), directory.nameOf(a.getApplicantId()),
                    a.getCourseTitle(), a.getStatus(), a.getStartDate(), a.getEndDate()));
        }
        return new Impact(date, confirmed, rows);
    }

    @WriteTransaction
    public void createYear(User admin, int year, String sourceNote) {
        int current = clock.currentYear();
        if (year < current - 1 || year > current + 2) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Choose a year between " + (current - 1)
                    + " and " + (current + 2) + ".");
        }
        AdminValidation v = new AdminValidation();
        String note = v.required("sourceNote", sourceNote, 400, "source of the holiday list");
        v.throwIfAny();
        if (years.existsById(year)) {
            throw new BusinessException(ErrorCode.CONFLICT, "The calendar for " + year + " already exists.");
        }
        years.saveAndFlush(TrainingCalendarYear.draft(year, note, clock.now()));
        record(admin, String.valueOf(year), "CALENDAR_CREATED", null, "DRAFT", note);
    }

    /** Adds the bundled official dates that are missing; returns how many were added. */
    @WriteTransaction
    public int importBundled(User admin, int year) {
        TrainingCalendarYear calendarYear = lockYear(year);
        int added = 0;
        for (OfficialHolidayData.Entry entry : official.forYear(year)) {
            if (!holidays.existsById(entry.date())) {
                holidays.save(ExcludedDays.create(entry.date(), entry.name(), OfficialHolidayData.SOURCE_NOTE,
                        clock.now()));
                added++;
            }
        }
        if (added > 0) {
            reopenIfConfirmed(admin, calendarYear, "Bundled official holidays imported");
            record(admin, String.valueOf(year), "HOLIDAYS_IMPORTED", null, null, added + " holiday(s) added");
        }
        return added;
    }

    @WriteTransaction
    public void addHoliday(User admin, LocalDate date, String name, String sourceNote) {
        AdminValidation v = new AdminValidation();
        if (date == null) {
            v.reject("date", "Enter the date.");
        }
        String holidayName = v.required("name", name, 120, "holiday name");
        String note = v.required("sourceNote", sourceNote, 400, "source");
        v.throwIfAny();
        TrainingCalendarYear calendarYear = lockYear(date.getYear());
        if (holidays.existsById(date)) {
            throw new BusinessException(ErrorCode.CONFLICT, date + " is already a public holiday.",
                    Map.of("date", "Already a public holiday."));
        }
        holidays.save(ExcludedDays.create(date, holidayName, note, clock.now()));
        reopenIfConfirmed(admin, calendarYear, "Holiday added on " + date);
        record(admin, date.toString(), "HOLIDAY_ADDED", null, null, holidayName);
    }

    /** Renaming a holiday or correcting its source does not change which days are working days. */
    @WriteTransaction
    public void describeHoliday(User admin, LocalDate date, String name, String sourceNote) {
        AdminValidation v = new AdminValidation();
        String holidayName = v.required("name", name, 120, "holiday name");
        String note = v.required("sourceNote", sourceNote, 400, "source");
        v.throwIfAny();
        lockYear(date.getYear());
        ExcludedDays holiday = holidays.findById(date).orElseThrow(NotFoundException::new);
        holiday.describe(holidayName, note, clock.now());
        record(admin, date.toString(), "HOLIDAY_RENAMED", null, null, holidayName);
    }

    @WriteTransaction
    public void removeHoliday(User admin, LocalDate date) {
        TrainingCalendarYear calendarYear = lockYear(date.getYear());
        ExcludedDays holiday = holidays.findById(date).orElseThrow(NotFoundException::new);
        String name = holiday.getName();
        holidays.delete(holiday);
        reopenIfConfirmed(admin, calendarYear, "Holiday removed on " + date);
        record(admin, date.toString(), "HOLIDAY_REMOVED", null, null, name);
    }

    @WriteTransaction
    public void confirmYear(User admin, int year, String sourceNote) {
        AdminValidation v = new AdminValidation();
        String note = v.required("sourceNote", sourceNote, 400, "source of the holiday list");
        v.throwIfAny();
        TrainingCalendarYear calendarYear = lockYear(year);
        long count = holidays.countByHolidayDateBetween(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        if (count == 0) {
            throw new BusinessException(ErrorCode.RULE_VIOLATION, "Add the public holidays of " + year
                    + " before confirming the calendar.");
        }
        String from = calendarYear.getStatus().name();
        calendarYear.confirm(admin.getUserId(), (int) count, note, clock.now());
        record(admin, String.valueOf(year), "CALENDAR_CONFIRMED", from, "CONFIRMED", count + " holidays; " + note);
    }

    private TrainingCalendarYear lockYear(int year) {
        return years.lockById(year).orElseThrow(() -> new BusinessException(ErrorCode.RULE_VIOLATION,
                "Create the " + year + " calendar before adding holidays to it."));
    }

    private void reopenIfConfirmed(User admin, TrainingCalendarYear calendarYear, String reason) {
        if (calendarYear.isConfirmed()) {
            calendarYear.reopen(clock.now());
            record(admin, calendarYear.getCalendarYear().toString(), "CALENDAR_REOPENED", "CONFIRMED", "DRAFT", reason);
        }
    }

    private void record(User admin, String key, String eventType, String from, String to, String reason) {
        audit.record(new AuditService.Change(AggregateType.HOLIDAY, key, eventType, admin.getUserId(), from, to,
                reason, Map.of()));
    }
}
