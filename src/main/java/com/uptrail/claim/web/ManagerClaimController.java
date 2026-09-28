package com.uptrail.claim.web;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.uptrail.approval.service.ManagerApplicationService;
import com.uptrail.claim.service.ClaimCommandService;
import com.uptrail.claim.service.ClaimCommandService.Decision;
import com.uptrail.claim.service.ClaimQueryService;
import com.uptrail.identity.service.UptrailUserPrincipal;
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
    private final ManagerApplicationService managerApplications;

    public ManagerClaimController(ClaimQueryService queries, ClaimCommandService commands,
            ManagerApplicationService managerApplications) {
        this.queries = queries;
        this.commands = commands;
        this.managerApplications = managerApplications;
    }

    @GetMapping("/manager/claims")
    public String claims(@AuthenticationPrincipal UptrailUserPrincipal principal,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            HttpServletRequest request, Model model) {
        model.addAttribute("view",
                Paging.view(queries.pendingForManager(principal.actor(), Paging.request(page, size)), request));
        model.addAttribute("pendingCount", managerApplications.pendingCount(principal.actor()));
        model.addAttribute("claimCount", queries.pendingCount(principal.actor()));
        model.addAttribute("tab", "claims");
        return "manager/claims";
    }

    @GetMapping("/manager/claims/{id}")
    public String review(@AuthenticationPrincipal UptrailUserPrincipal principal, @PathVariable Long id, Model model) {
        model.addAttribute("claim", queries.managerDetail(principal.actor(), id));
        return "manager/claim-review";
    }

    @PostMapping("/manager/claims/{id}/decision")
    public String decide(@AuthenticationPrincipal UptrailUserPrincipal principal, @PathVariable Long id,
            @RequestParam(required = false) Decision decision, @RequestParam(required = false) String reason,
            @RequestParam(required = false) Long expectedVersion, RedirectAttributes redirect) {
        try {
            commands.decide(principal.actor(), id, decision, reason, expectedVersion);
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
