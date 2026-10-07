package com.uptrail.controller;

import org.springframework.web.bind.annotation.ModelAttribute;

import com.uptrail.model.User;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.uptrail.service.HolidayAdminService;
import com.uptrail.shared.time.BusinessClock;

/**
 * Public holiday calendars. Adding or removing a date first shows the applications it affects and that a
 * confirmed year will return to draft; only a second, explicit confirmation applies the change.
 */
@Controller
public class AdminHolidayController {

    private final HolidayAdminService holidays;
    private final BusinessClock clock;

    public AdminHolidayController(HolidayAdminService holidays, BusinessClock clock) {
        this.holidays = holidays;
        this.clock = clock;
    }

    @GetMapping("/admin/holidays")
    public String page(@RequestParam(required = false) Integer year, Model model) {
        var years = holidays.selectableYears();
        int selected = year != null ? year : clock.currentYear();
        model.addAttribute("years", years.contains(selected) ? years
                : java.util.stream.Stream.concat(years.stream(), java.util.stream.Stream.of(selected)).sorted().toList());
        model.addAttribute("calendar", holidays.year(selected));
        return "admin/holidays";
    }

    @PostMapping("/admin/holidays/years")
    public String createYear(@ModelAttribute(value = "user", binding = false) User user, @RequestParam int year,
            @RequestParam(required = false) String sourceNote, RedirectAttributes redirect) {
        return AdminActions.run(redirect, "/admin/holidays?year=" + year, () -> {
            holidays.createYear(user, year, sourceNote);
            return "Calendar " + year + " created as a draft. Add its holidays, then confirm it.";
        });
    }

    @PostMapping("/admin/holidays/years/{year}/import")
    public String importBundled(@ModelAttribute(value = "user", binding = false) User user, @PathVariable int year,
            RedirectAttributes redirect) {
        return AdminActions.run(redirect, "/admin/holidays?year=" + year, () -> {
            int added = holidays.importBundled(user, year);
            return added == 0 ? "All bundled holidays of " + year + " are already present."
                    : added + " holiday(s) imported. Review them against the official source, then confirm the year.";
        });
    }

    @PostMapping("/admin/holidays/years/{year}/confirm")
    public String confirm(@ModelAttribute(value = "user", binding = false) User user, @PathVariable int year,
            @RequestParam(required = false) String sourceNote, RedirectAttributes redirect) {
        return AdminActions.run(redirect, "/admin/holidays?year=" + year, () -> {
            holidays.confirmYear(user, year, sourceNote);
            return "Calendar " + year + " confirmed. Training days in " + year + " can now be counted.";
        });
    }

    @PostMapping("/admin/holidays/add")
    public String add(@ModelAttribute(value = "user", binding = false) User user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String name, @RequestParam(required = false) String sourceNote,
            @RequestParam(defaultValue = "false") boolean confirmed, Model model, RedirectAttributes redirect) {
        if (date == null) {
            redirect.addFlashAttribute("flashError", "Enter the date of the holiday.");
            return "redirect:/admin/holidays";
        }
        var impact = holidays.impact(date);
        if (!confirmed && impact.needsConfirmation()) {
            model.addAttribute("impact", impact);
            model.addAttribute("action", "add");
            model.addAttribute("name", name);
            model.addAttribute("sourceNote", sourceNote);
            return "admin/holiday-impact";
        }
        return AdminActions.run(redirect, "/admin/holidays?year=" + date.getYear(), () -> {
            holidays.addHoliday(user, date, name, sourceNote);
            return "Holiday added." + (impact.yearConfirmed() ? " The " + date.getYear()
                    + " calendar is now a draft again; confirm it after reviewing." : "");
        });
    }

    @PostMapping("/admin/holidays/{date}/describe")
    public String describe(@ModelAttribute(value = "user", binding = false) User user,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String name, @RequestParam(required = false) String sourceNote,
            RedirectAttributes redirect) {
        return AdminActions.run(redirect, "/admin/holidays?year=" + date.getYear(), () -> {
            holidays.describeHoliday(user, date, name, sourceNote);
            return "Holiday details saved.";
        });
    }

    @PostMapping("/admin/holidays/{date}/remove")
    public String remove(@ModelAttribute(value = "user", binding = false) User user,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "false") boolean confirmed, Model model, RedirectAttributes redirect) {
        var impact = holidays.impact(date);
        if (!confirmed && impact.needsConfirmation()) {
            model.addAttribute("impact", impact);
            model.addAttribute("action", "remove");
            return "admin/holiday-impact";
        }
        return AdminActions.run(redirect, "/admin/holidays?year=" + date.getYear(), () -> {
            holidays.removeHoliday(user, date);
            return "Holiday removed." + (impact.yearConfirmed() ? " The " + date.getYear()
                    + " calendar is now a draft again; confirm it after reviewing." : "");
        });
    }
}
