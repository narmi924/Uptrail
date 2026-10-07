package com.uptrail.controller;

import org.springframework.web.bind.annotation.ModelAttribute;

import com.uptrail.model.User;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.uptrail.model.ApplicationStatus;
import com.uptrail.service.ManagerService;
import com.uptrail.shared.time.BusinessClock;
import com.uptrail.shared.web.Paging;

/**
 * Subordinate course history: works like the personal history, for one current direct report at a time.
 */
@Controller
public class ManagerTeamController {

    private final ManagerService managerApplications;
    private final BusinessClock clock;

    public ManagerTeamController(ManagerService managerApplications, BusinessClock clock) {
        this.managerApplications = managerApplications;
        this.clock = clock;
    }

    @GetMapping("/manager/team/history")
    public String history(@ModelAttribute(value = "user", binding = false) User user,
            @RequestParam(required = false) Long employeeId, @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            HttpServletRequest request, Model model) {
        var reports = managerApplications.directReports(user);
        int year = clock.currentYear();
        model.addAttribute("reports", reports);
        model.addAttribute("year", year);
        model.addAttribute("statuses", ApplicationStatus.values());
        model.addAttribute("status", status);
        model.addAttribute("employeeId", employeeId);
        if (employeeId != null) {
            var result = managerApplications.teamHistory(user, employeeId, year, status,
                    Paging.request(page, size));
            model.addAttribute("view", Paging.view(result, request));
            model.addAttribute("selected", reports.stream().filter(r -> r.id().equals(employeeId)).findFirst()
                    .orElse(null));
        }
        return "manager/team-history";
    }

    @GetMapping("/manager/team/applications/{id}")
    public String detail(@ModelAttribute(value = "user", binding = false) User user, @PathVariable Long id,
            Model model) {
        model.addAttribute("app", managerApplications.teamDetail(user, id));
        return "manager/team-detail";
    }
}
