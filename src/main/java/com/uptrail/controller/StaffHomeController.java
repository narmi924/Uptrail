package com.uptrail.controller;

import org.springframework.web.bind.annotation.ModelAttribute;

import com.uptrail.model.User;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.uptrail.service.ApplicationQueryService;
import com.uptrail.service.StaffService;

@Controller
public class StaffHomeController {

    private final StaffService directory;
    private final ApplicationQueryService queries;

    public StaffHomeController(StaffService directory, ApplicationQueryService queries) {
        this.directory = directory;
        this.queries = queries;
    }

    @GetMapping({"/staff", "/staff/home"})
    public String dashboard(@ModelAttribute(value = "user", binding = false) User user, Model model) {
        model.addAttribute("profile", directory.card(user.getUserId()));
        model.addAttribute("dash", queries.dashboard(user));
        return "employee/dashboard";
    }
}
