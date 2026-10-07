package com.uptrail.controller;

import org.springframework.web.bind.annotation.ModelAttribute;

import com.uptrail.model.User;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.uptrail.service.EntitlementQueryService;
import com.uptrail.shared.web.Paging;

/**
 * My Entitlement: the year's balance and the ledger movements behind it.
 */
@Controller
public class StaffEntitlementController {

    private final EntitlementQueryService entitlements;

    public StaffEntitlementController(EntitlementQueryService entitlements) {
        this.entitlements = entitlements;
    }

    @GetMapping("/staff/entitlement")
    public String page(@ModelAttribute(value = "user", binding = false) User user,
            @RequestParam(required = false) Integer year, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, HttpServletRequest request, Model model) {
        EntitlementQueryService.Overview overview = entitlements.overview(user, year);
        model.addAttribute("overview", overview);
        model.addAttribute("view", Paging.view(Paging.slice(overview.ledger(), Paging.request(page, size)), request));
        return "employee/entitlement";
    }
}
