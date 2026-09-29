package com.uptrail.approval.web;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.uptrail.application.service.ApplicationCommandService;
import com.uptrail.application.service.ApplicationCommandService.Decision;
import com.uptrail.approval.service.ManagerApplicationService;
import com.uptrail.claim.service.ClaimQueryService;
import com.uptrail.identity.service.UptrailUserPrincipal;
import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.error.NotFoundException;
import com.uptrail.shared.web.Paging;

/**
 * Manager worklist and review page. Decisions are POSTs with a mandatory reason and the version the manager
 * reviewed; a changed application is refused rather than approved on outdated information.
 */
@Controller
public class ManagerApprovalController {

    private final ManagerApplicationService managerApplications;
    private final ApplicationCommandService commands;
    private final ClaimQueryService claims;

    public ManagerApprovalController(ManagerApplicationService managerApplications,
            ApplicationCommandService commands, ClaimQueryService claims) {
        this.managerApplications = managerApplications;
        this.commands = commands;
        this.claims = claims;
    }

    @GetMapping("/manager/approvals")
    public String approvals(@AuthenticationPrincipal UptrailUserPrincipal principal,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            HttpServletRequest request, Model model) {
        var groups = managerApplications.pendingGroups(principal.actor(), Paging.request(page, size));
        model.addAttribute("view", Paging.view(groups, request));
        model.addAttribute("pendingCount", managerApplications.pendingCount(principal.actor()));
        model.addAttribute("claimCount", claims.pendingCount(principal.actor()));
        model.addAttribute("tab", "applications");
        return "manager/approvals";
    }

    @GetMapping("/manager/applications/{id}")
    public String review(@AuthenticationPrincipal UptrailUserPrincipal principal, @PathVariable Long id, Model model) {
        model.addAttribute("review", managerApplications.review(principal.actor(), id));
        return "manager/review";
    }

    @PostMapping("/manager/applications/{id}/decision")
    public String decide(@AuthenticationPrincipal UptrailUserPrincipal principal, @PathVariable Long id,
            @RequestParam(required = false) Decision decision, @RequestParam(required = false) String reason,
            @RequestParam(required = false) Long expectedVersion, RedirectAttributes redirect) {
        try {
            if (decision == null) {
                throw new BusinessException(com.uptrail.shared.error.ErrorCode.VALIDATION_FAILED,
                        "Choose Approve or Reject.");
            }
            commands.decide(principal.actor(), id, decision, reason, expectedVersion);
            redirect.addFlashAttribute("flashSuccess", decision == Decision.APPROVE
                    ? "Application approved. The applicant will be notified by email with your reason."
                    : "Application rejected. The applicant will be notified by email with your reason.");
            return "redirect:/manager/approvals";
        } catch (NotFoundException e) {
            throw e;
        } catch (BusinessException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
            redirect.addFlashAttribute("reasonDraft", reason);
            redirect.addFlashAttribute("decisionDraft", decision);
            return "redirect:/manager/applications/" + id;
        }
    }
}
