package com.uptrail.claim.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.uptrail.claim.service.ClaimCommandService;
import com.uptrail.claim.service.ClaimQueryService;
import com.uptrail.claim.service.ClaimQueryService.NewClaim;
import com.uptrail.claim.service.DocumentStorage.Upload;
import com.uptrail.identity.service.UptrailUserPrincipal;
import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.error.ErrorCode;
import com.uptrail.shared.error.NotFoundException;
import com.uptrail.shared.web.Paging;

/**
 * Employee fee claims: list, new claim form, detail with revisions, and resubmission after a rejection.
 * Rule failures re-display the form with the entered amount; files must be chosen again because browsers
 * never pre-fill file inputs.
 */
@Controller
public class EmployeeClaimController {

    private final ClaimQueryService queries;
    private final ClaimCommandService commands;

    public EmployeeClaimController(ClaimQueryService queries, ClaimCommandService commands) {
        this.queries = queries;
        this.commands = commands;
    }

    @GetMapping("/employee/claims")
    public String list(@AuthenticationPrincipal UptrailUserPrincipal principal,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            HttpServletRequest request, Model model) {
        model.addAttribute("claimable", queries.claimable(principal.actor()));
        model.addAttribute("view", Paging.view(queries.ownClaims(principal.actor(), Paging.request(page, size)), request));
        return "employee/claims/list";
    }

    @GetMapping("/employee/claims/new")
    public String form(@AuthenticationPrincipal UptrailUserPrincipal principal,
            @RequestParam(required = false) Long applicationId, Model model, RedirectAttributes redirect) {
        if (applicationId == null) {
            return "redirect:/employee/claims";
        }
        NewClaim newClaim = queries.newClaim(principal.actor(), applicationId);
        if (newClaim.existingClaimId() != null) {
            redirect.addFlashAttribute("flashInfo", "This course already has a fee claim.");
            return "redirect:/employee/claims/" + newClaim.existingClaimId();
        }
        return showForm(newClaim, null, true, Map.of(), null, model);
    }

    @PostMapping("/employee/claims")
    public String submit(@AuthenticationPrincipal UptrailUserPrincipal principal,
            @RequestParam(required = false) Long applicationId, @RequestParam(required = false) String amount,
            @RequestParam(defaultValue = "false") boolean paidByEmployee,
            @RequestParam(required = false) MultipartFile receipt,
            @RequestParam(required = false) MultipartFile certificate, Model model, RedirectAttributes redirect) {
        if (applicationId == null) {
            throw new NotFoundException();
        }
        try {
            Long claimId = commands.submit(principal.actor(), applicationId, parseAmount(amount), paidByEmployee,
                    upload(receipt), upload(certificate));
            redirect.addFlashAttribute("flashSuccess",
                    "Claim submitted. Your approving manager has been notified by email.");
            return "redirect:/employee/claims/" + claimId;
        } catch (NotFoundException e) {
            throw e;
        } catch (BusinessException e) {
            NewClaim newClaim = queries.newClaim(principal.actor(), applicationId);
            if (newClaim.existingClaimId() != null) {
                redirect.addFlashAttribute("flashError", e.getMessage());
                return "redirect:/employee/claims/" + newClaim.existingClaimId();
            }
            return showForm(newClaim, amount, paidByEmployee, e.fieldErrors(), e.getMessage(), model);
        }
    }

    @GetMapping("/employee/claims/{id}")
    public String detail(@AuthenticationPrincipal UptrailUserPrincipal principal, @PathVariable Long id, Model model) {
        model.addAttribute("claim", queries.ownDetail(principal.actor(), id));
        model.addAttribute("maxBytes", queries.maxDocumentBytes());
        return "employee/claims/detail";
    }

    @PostMapping("/employee/claims/{id}/resubmit")
    public String resubmit(@AuthenticationPrincipal UptrailUserPrincipal principal, @PathVariable Long id,
            @RequestParam(required = false) Long expectedVersion, @RequestParam(required = false) String amount,
            @RequestParam(defaultValue = "false") boolean paidByEmployee,
            @RequestParam(required = false) MultipartFile receipt,
            @RequestParam(required = false) MultipartFile certificate, Model model, RedirectAttributes redirect) {
        try {
            commands.resubmit(principal.actor(), id, expectedVersion, parseAmount(amount), paidByEmployee,
                    upload(receipt), upload(certificate));
            redirect.addFlashAttribute("flashSuccess",
                    "Revised claim submitted. Your approving manager has been notified by email.");
            return "redirect:/employee/claims/" + id;
        } catch (NotFoundException e) {
            throw e;
        } catch (BusinessException e) {
            if (e.code() == ErrorCode.STALE_VERSION || e.code() == ErrorCode.INVALID_STATE) {
                redirect.addFlashAttribute("flashError", e.getMessage());
                return "redirect:/employee/claims/" + id;
            }
            model.addAttribute("claim", queries.ownDetail(principal.actor(), id));
            model.addAttribute("maxBytes", queries.maxDocumentBytes());
            model.addAttribute("amountDraft", amount);
            model.addAttribute("paidDraft", paidByEmployee);
            model.addAttribute("fieldErrors", e.fieldErrors());
            model.addAttribute("businessError", e.getMessage());
            return "employee/claims/detail";
        }
    }

    private String showForm(NewClaim newClaim, String amount, boolean paidByEmployee, Map<String, String> fieldErrors,
            String businessError, Model model) {
        model.addAttribute("course", newClaim.application());
        model.addAttribute("ineligible", newClaim.ineligibleReason());
        model.addAttribute("amountDraft", amount != null ? amount : newClaim.application().fee().toPlainString());
        model.addAttribute("paidDraft", paidByEmployee);
        model.addAttribute("fieldErrors", fieldErrors);
        model.addAttribute("businessError", businessError);
        model.addAttribute("maxBytes", queries.maxDocumentBytes());
        return "employee/claims/form";
    }

    static BigDecimal parseAmount(String amount) {
        if (amount == null || amount.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(amount.strip().replace(",", ""));
        } catch (NumberFormatException e) {
            String message = "Enter the amount as a number, for example 250.00.";
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, message, Map.of("amount", message));
        }
    }

    static Upload upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            return new Upload(file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read an uploaded file", e);
        }
    }
}
