package com.uptrail.entitlement.web;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.uptrail.entitlement.service.EntitlementQueryService;
import com.uptrail.identity.service.UptrailUserPrincipal;
import com.uptrail.shared.web.Paging;

/**
 * My Entitlement: the year's balance and the ledger movements behind it.
 */
@Controller
public class EmployeeEntitlementController {

    private final EntitlementQueryService entitlements;

    public EmployeeEntitlementController(EntitlementQueryService entitlements) {
        this.entitlements = entitlements;
    }

    @GetMapping("/employee/entitlement")
    public String page(@AuthenticationPrincipal UptrailUserPrincipal principal,
            @RequestParam(required = false) Integer year, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, HttpServletRequest request, Model model) {
        EntitlementQueryService.Overview overview = entitlements.overview(principal.actor(), year);
        model.addAttribute("overview", overview);
        model.addAttribute("view", Paging.view(Paging.slice(overview.ledger(), Paging.request(page, size)), request));
        return "employee/entitlement";
    }
}
