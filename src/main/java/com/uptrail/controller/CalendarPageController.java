package com.uptrail.controller;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.uptrail.model.CategoryCode;
import com.uptrail.service.CatalogueQueryService;
import com.uptrail.service.CalendarQueryService;
import com.uptrail.shared.time.BusinessClock;

/**
 * Training calendar page. The browser loads the month from {@code /api/v1/calendar}; the server also renders
 * a plain list of the month for browsers without JavaScript.
 */
@Controller
public class CalendarPageController {

    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    public record MonthOption(String value, String label) {
    }

    private final CalendarQueryService calendar;
    private final CatalogueQueryService catalogue;
    private final BusinessClock clock;

    public CalendarPageController(CalendarQueryService calendar, CatalogueQueryService catalogue, BusinessClock clock) {
        this.calendar = calendar;
        this.catalogue = catalogue;
        this.clock = clock;
    }

    @GetMapping("/calendar")
    public String page(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) CategoryCode category, Model model) {
        YearMonth current = clock.currentMonth();
        YearMonth selected = month == null ? current : month;
        // The picker offers a year either side of today; a month further away (from a bookmark or the
        // previous/next links) is added on its own rather than widening the list to reach it.
        List<MonthOption> options = new ArrayList<>();
        YearMonth first = current.minusMonths(12);
        YearMonth last = current.plusMonths(12);
        if (selected.isBefore(first)) {
            options.add(new MonthOption(selected.toString(), LABEL.format(selected)));
        }
        for (YearMonth m = first; !m.isAfter(last); m = m.plusMonths(1)) {
            options.add(new MonthOption(m.toString(), LABEL.format(m)));
        }
        if (selected.isAfter(last)) {
            options.add(new MonthOption(selected.toString(), LABEL.format(selected)));
        }
        model.addAttribute("month", selected);
        model.addAttribute("monthLabel", LABEL.format(selected));
        model.addAttribute("today", clock.today());
        model.addAttribute("months", options);
        model.addAttribute("category", category);
        model.addAttribute("categories", catalogue.categories());
        model.addAttribute("data", calendar.month(selected, category));
        return "shared/calendar";
    }
}
