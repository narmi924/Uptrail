package com.uptrail.controller;

import org.springframework.web.bind.annotation.ModelAttribute;

import com.uptrail.model.User;
import com.uptrail.model.Manager;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.uptrail.service.CourseApplicationService;
import com.uptrail.service.CourseApplicationService.Decision;
import com.uptrail.service.ManagerService;
import com.uptrail.service.ClaimQueryService;
import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.error.NotFoundException;
import com.uptrail.shared.web.Paging;

/**
 * Manager worklist and review page. Decisions are POSTs with a mandatory reason and the version the manager
 * reviewed; a changed application is refused rather than approved on outdated information.
 */
@Controller
public class ManagerController {

    private final ManagerService managerApplications;
    private final ClaimQueryService claims;

    public ManagerController(ManagerService managerApplications, ClaimQueryService claims) {
        this.managerApplications = managerApplications;
        this.claims = claims;
    }

    @GetMapping({"/manager", "/manager/home"})
    public String managerHome(@ModelAttribute(value = "user", binding = false) User user, Model model) {
        if (!(user instanceof Manager)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        }
        model.addAttribute("pendingCount", managerApplications.pendingCount(user));
        model.addAttribute("claimCount", claims.pendingCount(user));
        model.addAttribute("teamCount", managerApplications.directReports(user).size());
        return "manager/home";
    }

    @GetMapping("/manager/approvals")
    public String approvals(@ModelAttribute(value = "user", binding = false) User user,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            HttpServletRequest request, Model model) {
        var groups = managerApplications.pendingGroups(user, Paging.request(page, size));
        model.addAttribute("view", Paging.view(groups, request));
        model.addAttribute("pendingCount", managerApplications.pendingCount(user));
        model.addAttribute("claimCount", claims.pendingCount(user));
        model.addAttribute("tab", "applications");
        return "manager/approvals";
    }

    @GetMapping("/manager/applications/{id}")
    public String review(@ModelAttribute(value = "user", binding = false) User user, @PathVariable Long id, Model model) {
        model.addAttribute("review", managerApplications.review(user, id));
        return "manager/review";
    }

    @PostMapping("/manager/applications/{id}/decision")
    public String decide(@ModelAttribute(value = "user", binding = false) User user, @PathVariable Long id,
            @RequestParam(required = false) Decision decision, @RequestParam(required = false) String reason,
            @RequestParam(required = false) Long expectedVersion, RedirectAttributes redirect) {
        try {
            if (decision == null) {
                throw new BusinessException(com.uptrail.shared.error.ErrorCode.VALIDATION_FAILED,
                        "Choose Approve or Reject.");
            }
            if (!(user instanceof Manager manager)) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
            }
            if (decision == Decision.APPROVE) {
                managerApplications.approveCourseApplication(manager, id, reason, expectedVersion);
            } else {
                managerApplications.rejectCourseApplication(manager, id, reason, expectedVersion);
            }
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
