package com.uptrail.application.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.uptrail.application.service.ApplicationQueryService;
import com.uptrail.identity.service.UptrailUserPrincipal;
import com.uptrail.organisation.service.EmployeeDirectoryService;

@Controller
public class EmployeeDashboardController {

    private final EmployeeDirectoryService directory;
    private final ApplicationQueryService queries;

    public EmployeeDashboardController(EmployeeDirectoryService directory, ApplicationQueryService queries) {
        this.directory = directory;
        this.queries = queries;
    }

    @GetMapping("/employee/dashboard")
    public String dashboard(@AuthenticationPrincipal UptrailUserPrincipal principal, Model model) {
        model.addAttribute("profile", directory.card(principal.getEmployeeId()));
        model.addAttribute("dash", queries.dashboard(principal.actor()));
        return "employee/dashboard";
    }
}
