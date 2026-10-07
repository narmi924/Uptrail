package com.uptrail.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.uptrail.service.AdminOverviewService;

@Controller
public class AdminDashboardController {

    private final AdminOverviewService overviewService;

    public AdminDashboardController(AdminOverviewService overviewService) {
        this.overviewService = overviewService;
    }

    @GetMapping("/admin/home")
    public String dashboard(Model model) {
        model.addAttribute("overview", overviewService.overview());
        return "admin/dashboard";
    }
}
