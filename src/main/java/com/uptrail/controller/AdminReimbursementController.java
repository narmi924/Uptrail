package com.uptrail.controller;

import org.springframework.web.bind.annotation.ModelAttribute;

import com.uptrail.model.User;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.uptrail.service.ClaimCommandService;
import com.uptrail.service.ClaimQueryService;
import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.error.NotFoundException;
import com.uptrail.shared.web.Paging;

/**
 * Reimbursement registration queue for administrators. Registration is a record only: Uptrail has no bank
 * interface and issues a simulated reference.
 */
@Controller
public class AdminReimbursementController {

    private final ClaimQueryService queries;
    private final ClaimCommandService commands;

    public AdminReimbursementController(ClaimQueryService queries, ClaimCommandService commands) {
        this.queries = queries;
        this.commands = commands;
    }

    @GetMapping("/admin/reimbursements")
    public String queue(@RequestParam(defaultValue = "awaiting") String tab,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            HttpServletRequest request, Model model) {
        boolean registered = "registered".equals(tab);
        model.addAttribute("tab", registered ? "registered" : "awaiting");
        model.addAttribute("awaitingCount", queries.awaitingCount());
        model.addAttribute("view", Paging.view(registered ? queries.reimbursed(Paging.request(page, size))
                : queries.awaitingReimbursement(Paging.request(page, size)), request));
        return "admin/reimbursements";
    }

    @GetMapping("/admin/claims/{id}")
    public String detail(@ModelAttribute(value = "user", binding = false) User user, @PathVariable Long id, Model model) {
        model.addAttribute("claim", queries.adminDetail(user, id));
        return "admin/claim-detail";
    }

    @PostMapping("/admin/claims/{id}/reimburse")
    public String register(@ModelAttribute(value = "user", binding = false) User user, @PathVariable Long id,
            @RequestParam(required = false) Long expectedVersion, RedirectAttributes redirect) {
        try {
            String reference = commands.registerReimbursement(user, id, expectedVersion);
            redirect.addFlashAttribute("flashSuccess", "Reimbursement registered with reference " + reference
                    + ". Recorded in demo; no payment is initiated.");
            return "redirect:/admin/reimbursements";
        } catch (NotFoundException e) {
            throw e;
        } catch (BusinessException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/admin/claims/" + id;
        }
    }
}
