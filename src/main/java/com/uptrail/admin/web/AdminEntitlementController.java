package com.uptrail.admin.web;

import java.math.BigDecimal;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.uptrail.admin.service.EntitlementAdminService;
import com.uptrail.identity.service.UptrailUserPrincipal;
import com.uptrail.organisation.domain.Designation;
import com.uptrail.shared.web.Paging;

@Controller
public class AdminEntitlementController {

    private final EntitlementAdminService entitlements;

    public AdminEntitlementController(EntitlementAdminService entitlements) {
        this.entitlements = entitlements;
    }

    @GetMapping("/admin/entitlements")
    public String list(@RequestParam(required = false) Integer year, @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            HttpServletRequest request, Model model) {
        var years = entitlements.editableYears();
        int selected = year != null && years.contains(year) ? year : years.get(0);
        model.addAttribute("view", Paging.view(entitlements.list(selected, q, Paging.request(page, size)), request));
        model.addAttribute("year", selected);
        model.addAttribute("years", years);
        model.addAttribute("q", q);
        model.addAttribute("defaults", java.util.Arrays.stream(Designation.values())
                .map(d -> java.util.Map.entry(d, entitlements.defaultsFor(d))).toList());
        return "admin/entitlements";
    }

    @PostMapping("/admin/entitlements/{employeeId}/{year}")
    public String save(@AuthenticationPrincipal UptrailUserPrincipal principal, @PathVariable Long employeeId,
            @PathVariable int year, @RequestParam(required = false) BigDecimal days,
            @RequestParam(required = false) BigDecimal budget, @RequestParam(required = false) String reason,
            @RequestParam(required = false) String returnTo, RedirectAttributes redirect) {
        String target = returnTo != null && returnTo.startsWith("/admin/") ? returnTo : "/admin/entitlements?year=" + year;
        return AdminActions.run(redirect, target, () -> {
            entitlements.save(principal.actor(), employeeId, year, days, budget, reason);
            return "Entitlement for " + year + " saved.";
        });
    }

    @PostMapping("/admin/entitlements/{year}/open-missing")
    public String openMissing(@AuthenticationPrincipal UptrailUserPrincipal principal, @PathVariable int year,
            RedirectAttributes redirect) {
        return AdminActions.run(redirect, "/admin/entitlements?year=" + year, () -> {
            int opened = entitlements.openMissing(principal.actor(), year);
            return opened + " account(s) opened for " + year + " with the designation defaults.";
        });
    }
}
