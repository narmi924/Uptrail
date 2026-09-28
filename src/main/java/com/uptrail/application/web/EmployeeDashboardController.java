package com.uptrail.application.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.uptrail.identity.service.UptrailUserPrincipal;
import com.uptrail.organisation.service.EmployeeDirectoryService;

@Controller
public class EmployeeDashboardController {

    private final EmployeeDirectoryService directory;

    public EmployeeDashboardController(EmployeeDirectoryService directory) {
        this.directory = directory;
    }

    @GetMapping("/employee/dashboard")
    public String dashboard(@AuthenticationPrincipal UptrailUserPrincipal principal, Model model) {
        model.addAttribute("profile", directory.card(principal.getEmployeeId()));
        return "employee/dashboard";
    }
}
