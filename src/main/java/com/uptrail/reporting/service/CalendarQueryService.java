package com.uptrail.reporting.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uptrail.application.domain.ApplicationStatus;
import com.uptrail.application.domain.CourseApplication;
import com.uptrail.application.repository.CourseApplicationRepository;
import com.uptrail.catalogue.domain.CategoryCode;
import com.uptrail.catalogue.service.CatalogueQueryService;
import com.uptrail.catalogue.service.HolidayCalendarService;
import com.uptrail.entitlement.domain.Session;
import com.uptrail.organisation.service.EmployeeDirectoryService;

/**
 * Training calendar shared by all signed-in users. It shows approved courses and completed courses (which
 * were approved) with only the name, course, category and dates; cancelled, rejected, deleted and pending
 * applications are not shown, and no fee, reason or document is exposed.
 */
@Service
@Transactional(readOnly = true)
public class CalendarQueryService {

    public static final Set<ApplicationStatus> SHOWN = Set.of(ApplicationStatus.APPROVED, ApplicationStatus.COMPLETED);

    public record Entry(String employeeName, String courseTitle, CategoryCode category, String categoryName,
            LocalDate startDate, Session startSession, LocalDate endDate, Session endSession) {
    }

    public record Holiday(LocalDate date, String name) {
    }

    public record Month(YearMonth month, List<Entry> entries, List<Holiday> holidays) {
    }

    private final CourseApplicationRepository applications;
    private final EmployeeDirectoryService directory;
    private final CatalogueQueryService catalogue;
    private final HolidayCalendarService holidays;

    public CalendarQueryService(CourseApplicationRepository applications, EmployeeDirectoryService directory,
            CatalogueQueryService catalogue, HolidayCalendarService holidays) {
        this.applications = applications;
        this.directory = directory;
        this.catalogue = catalogue;
        this.holidays = holidays;
    }

    public Month month(YearMonth month, CategoryCode category) {
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        List<CourseApplication> found = applications.findShownInPeriod(SHOWN, first, last);
        Map<Long, String> names = directory.namesOf(found.stream().map(CourseApplication::getEmployeeId).toList());
        Map<CategoryCode, String> categoryNames = catalogue.categoryNames();
        List<Entry> entries = found.stream()
                .filter(a -> category == null || a.getCategory() == category)
                .map(a -> new Entry(names.get(a.getEmployeeId()), a.getCourseTitle(), a.getCategory(),
                        categoryNames.getOrDefault(a.getCategory(), a.getCategory().defaultLabel()), a.getStartDate(),
                        a.getStartSession(), a.getEndDate(), a.getEndSession()))
                .toList();
        List<Holiday> monthHolidays = holidays.holidaysBetween(first, last).entrySet().stream()
                .map(e -> new Holiday(e.getKey(), e.getValue())).toList();
        return new Month(month, entries, monthHolidays);
    }
}
