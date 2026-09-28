package com.uptrail.admin.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.uptrail.admin.service.AdminOverviewService;

@Controller
public class AdminDashboardController {

    private final AdminOverviewService overviewService;

    public AdminDashboardController(AdminOverviewService overviewService) {
        this.overviewService = overviewService;
    }

    @GetMapping("/admin/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("overview", overviewService.overview());
        return "admin/dashboard";
    }
}
