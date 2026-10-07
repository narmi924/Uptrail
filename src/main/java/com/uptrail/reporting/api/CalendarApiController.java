package com.uptrail.reporting.api;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.uptrail.catalogue.domain.CategoryCode;
import com.uptrail.entitlement.domain.Session;
import com.uptrail.reporting.service.CalendarQueryService;
import com.uptrail.shared.time.BusinessClock;

/**
 * Training calendar data for the calendar page. Only names, course titles, categories and dates are
 * returned; the page renders the month grid and list from this response.
 */
@RestController
@RequestMapping("/api/v1/calendar")
@ConditionalOnProperty(prefix = "uptrail.web", name = "rest-enabled", havingValue = "true", matchIfMissing = true)
public class CalendarApiController {

    public record CalendarEntry(String employeeName, String courseTitle, CategoryCode category, String categoryName,
            LocalDate startDate, Session startSession, LocalDate endDate, Session endSession) {
    }

    public record HolidayEntry(LocalDate date, String name) {
    }

    public record CalendarResponse(String month, String previousMonth, String nextMonth, List<CalendarEntry> entries,
            List<HolidayEntry> holidays) {
    }

    private final CalendarQueryService calendar;
    private final BusinessClock clock;

    public CalendarApiController(CalendarQueryService calendar, BusinessClock clock) {
        this.calendar = calendar;
        this.clock = clock;
    }

    @GetMapping
    public CalendarResponse month(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) CategoryCode category) {
        YearMonth selected = month == null ? clock.currentMonth() : month;
        CalendarQueryService.Month result = calendar.month(selected, category);
        return new CalendarResponse(selected.toString(), selected.minusMonths(1).toString(),
                selected.plusMonths(1).toString(),
                result.entries().stream().map(e -> new CalendarEntry(e.employeeName(), e.courseTitle(), e.category(),
                        e.categoryName(), e.startDate(), e.startSession(), e.endDate(), e.endSession())).toList(),
                result.holidays().stream().map(h -> new HolidayEntry(h.date(), h.name())).toList());
    }
}
