package com.uptrail.admin.web;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.uptrail.admin.service.HolidayAdminService;
import com.uptrail.identity.service.UptrailUserPrincipal;
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
    public String createYear(@AuthenticationPrincipal UptrailUserPrincipal principal, @RequestParam int year,
            @RequestParam(required = false) String sourceNote, RedirectAttributes redirect) {
        return AdminActions.run(redirect, "/admin/holidays?year=" + year, () -> {
            holidays.createYear(principal.actor(), year, sourceNote);
            return "Calendar " + year + " created as a draft. Add its holidays, then confirm it.";
        });
    }

    @PostMapping("/admin/holidays/years/{year}/import")
    public String importBundled(@AuthenticationPrincipal UptrailUserPrincipal principal, @PathVariable int year,
            RedirectAttributes redirect) {
        return AdminActions.run(redirect, "/admin/holidays?year=" + year, () -> {
            int added = holidays.importBundled(principal.actor(), year);
            return added == 0 ? "All bundled holidays of " + year + " are already present."
                    : added + " holiday(s) imported. Review them against the official source, then confirm the year.";
        });
    }

    @PostMapping("/admin/holidays/years/{year}/confirm")
    public String confirm(@AuthenticationPrincipal UptrailUserPrincipal principal, @PathVariable int year,
            @RequestParam(required = false) String sourceNote, RedirectAttributes redirect) {
        return AdminActions.run(redirect, "/admin/holidays?year=" + year, () -> {
            holidays.confirmYear(principal.actor(), year, sourceNote);
            return "Calendar " + year + " confirmed. Training days in " + year + " can now be counted.";
        });
    }

    @PostMapping("/admin/holidays/add")
    public String add(@AuthenticationPrincipal UptrailUserPrincipal principal,
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
            holidays.addHoliday(principal.actor(), date, name, sourceNote);
            return "Holiday added." + (impact.yearConfirmed() ? " The " + date.getYear()
                    + " calendar is now a draft again; confirm it after reviewing." : "");
        });
    }

    @PostMapping("/admin/holidays/{date}/describe")
    public String describe(@AuthenticationPrincipal UptrailUserPrincipal principal,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String name, @RequestParam(required = false) String sourceNote,
            RedirectAttributes redirect) {
        return AdminActions.run(redirect, "/admin/holidays?year=" + date.getYear(), () -> {
            holidays.describeHoliday(principal.actor(), date, name, sourceNote);
            return "Holiday details saved.";
        });
    }

    @PostMapping("/admin/holidays/{date}/remove")
    public String remove(@AuthenticationPrincipal UptrailUserPrincipal principal,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "false") boolean confirmed, Model model, RedirectAttributes redirect) {
        var impact = holidays.impact(date);
        if (!confirmed && impact.needsConfirmation()) {
            model.addAttribute("impact", impact);
            model.addAttribute("action", "remove");
            return "admin/holiday-impact";
        }
        return AdminActions.run(redirect, "/admin/holidays?year=" + date.getYear(), () -> {
            holidays.removeHoliday(principal.actor(), date);
            return "Holiday removed." + (impact.yearConfirmed() ? " The " + date.getYear()
                    + " calendar is now a draft again; confirm it after reviewing." : "");
        });
    }
}
