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

import com.uptrail.service.ManagerService;
import com.uptrail.service.ClaimCommandService;
import com.uptrail.service.ClaimCommandService.Decision;
import com.uptrail.service.ClaimQueryService;
import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.error.NotFoundException;
import com.uptrail.shared.web.Paging;

/**
 * The fee-claims tab of the manager worklist and the claim review page. Like application decisions, a
 * claim decision needs a reason and the version the manager reviewed.
 */
@Controller
public class ManagerClaimController {

    private final ClaimQueryService queries;
    private final ClaimCommandService commands;
    private final ManagerService managerApplications;

    public ManagerClaimController(ClaimQueryService queries, ClaimCommandService commands,
            ManagerService managerApplications) {
        this.queries = queries;
        this.commands = commands;
        this.managerApplications = managerApplications;
    }

    @GetMapping("/manager/claims")
    public String claims(@ModelAttribute(value = "user", binding = false) User user,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            HttpServletRequest request, Model model) {
        model.addAttribute("view",
                Paging.view(queries.pendingForManager(user, Paging.request(page, size)), request));
        model.addAttribute("pendingCount", managerApplications.pendingCount(user));
        model.addAttribute("claimCount", queries.pendingCount(user));
        model.addAttribute("tab", "claims");
        return "manager/claims";
    }

    @GetMapping("/manager/claims/{id}")
    public String review(@ModelAttribute(value = "user", binding = false) User user, @PathVariable Long id, Model model) {
        model.addAttribute("claim", queries.managerDetail(user, id));
        return "manager/claim-review";
    }

    @PostMapping("/manager/claims/{id}/decision")
    public String decide(@ModelAttribute(value = "user", binding = false) User user, @PathVariable Long id,
            @RequestParam(required = false) Decision decision, @RequestParam(required = false) String reason,
            @RequestParam(required = false) Long expectedVersion, RedirectAttributes redirect) {
        try {
            commands.decide(user, id, decision, reason, expectedVersion);
            redirect.addFlashAttribute("flashSuccess", decision == Decision.APPROVE
                    ? "Claim approved. The claimant is notified by email; an administrator registers the reimbursement."
                    : "Claim rejected. The claimant is notified by email with your reason and can revise the claim.");
            return "redirect:/manager/claims";
        } catch (NotFoundException e) {
            throw e;
        } catch (BusinessException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
            redirect.addFlashAttribute("reasonDraft", reason);
            redirect.addFlashAttribute("decisionDraft", decision);
            return "redirect:/manager/claims/" + id;
        }
    }
}
