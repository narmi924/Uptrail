package com.uptrail.application.web;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.uptrail.application.domain.ApplicationStatus;
import com.uptrail.application.service.ApplicationCommandService;
import com.uptrail.application.service.ApplicationCommandService.SubmitResult;
import com.uptrail.application.service.ApplicationQueryService;
import com.uptrail.application.service.ApplicationViews;
import com.uptrail.catalogue.domain.CategoryCode;
import com.uptrail.catalogue.service.CatalogueQueryService;
import com.uptrail.identity.service.UptrailUserPrincipal;
import com.uptrail.organisation.service.EmployeeDirectoryService;
import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.time.BusinessClock;
import com.uptrail.shared.web.Paging;

/**
 * Employee pages: personal course history, application form and application detail.
 */
@Controller
@RequestMapping("/employee/applications")
public class EmployeeApplicationController {

    private static final String[] FORM_FIELDS = {"catalogueId", "category", "courseTitle", "providerName",
            "startDate", "endDate", "startSession", "endSession", "courseFee", "justification", "workDissemination",
            "clientRequestId", "expectedVersion"};

    private final ApplicationQueryService queries;
    private final ApplicationCommandService commands;
    private final CatalogueQueryService catalogue;
    private final EmployeeDirectoryService directory;
    private final BusinessClock clock;

    public EmployeeApplicationController(ApplicationQueryService queries, ApplicationCommandService commands,
            CatalogueQueryService catalogue, EmployeeDirectoryService directory, BusinessClock clock) {
        this.queries = queries;
        this.commands = commands;
        this.catalogue = catalogue;
        this.directory = directory;
        this.clock = clock;
    }

    @InitBinder("form")
    void allowOnlyFormFields(WebDataBinder binder) {
        binder.setAllowedFields(FORM_FIELDS);
    }

    @GetMapping
    public String history(@AuthenticationPrincipal UptrailUserPrincipal principal,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) CategoryCode category, @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            HttpServletRequest request, Model model) {
        int year = clock.currentYear();
        var result = queries.history(principal.getEmployeeId(), year, status, category, q, Paging.request(page, size));
        model.addAttribute("view", Paging.view(result, request));
        model.addAttribute("year", year);
        model.addAttribute("statuses", ApplicationStatus.values());
        model.addAttribute("categories", catalogue.categories());
        model.addAttribute("status", status);
        model.addAttribute("category", category);
        model.addAttribute("q", q);
        return "employee/applications/list";
    }

    @GetMapping("/new")
    public String newForm(@AuthenticationPrincipal UptrailUserPrincipal principal, Model model) {
        ApplicationForm form = new ApplicationForm();
        form.setClientRequestId(UUID.randomUUID().toString());
        model.addAttribute("form", form);
        return formView(principal, model, null);
    }

    @PostMapping
    public String submit(@AuthenticationPrincipal UptrailUserPrincipal principal,
            @ModelAttribute("form") ApplicationForm form, BindingResult binding, Model model,
            RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("businessError", "Some fields could not be read. Check the highlighted fields.");
            return formView(principal, model, null);
        }
        try {
            SubmitResult result = commands.submit(principal.actor(), form.toDetails(), form.getClientRequestId());
            redirect.addFlashAttribute("flashSuccess", result.replayed()
                    ? "This application was already submitted as " + result.referenceNo() + "."
                    : "Application " + result.referenceNo() + " was submitted. An email notification to your "
                            + "approving manager has been queued.");
            return "redirect:/employee/applications/" + result.applicationId();
        } catch (BusinessException e) {
            FormErrors.apply(e, binding, model);
            return formView(principal, model, null);
        }
    }

    @GetMapping("/{id}")
    public String detail(@AuthenticationPrincipal UptrailUserPrincipal principal, @PathVariable Long id,
            Model model) {
        model.addAttribute("app", queries.ownDetail(principal.actor(), id));
        return "employee/applications/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@AuthenticationPrincipal UptrailUserPrincipal principal, @PathVariable Long id, Model model,
            RedirectAttributes redirect) {
        ApplicationViews.Detail detail = queries.ownDetail(principal.actor(), id);
        if (!detail.actions().canEdit()) {
            redirect.addFlashAttribute("flashError", "Application " + detail.referenceNo() + " is "
                    + detail.status().label().toLowerCase() + " and can no longer be edited.");
            return "redirect:/employee/applications/" + id;
        }
        model.addAttribute("form", ApplicationForm.from(detail));
        return formView(principal, model, detail);
    }

    @PostMapping("/{id}/update")
    public String update(@AuthenticationPrincipal UptrailUserPrincipal principal, @PathVariable Long id,
            @ModelAttribute("form") ApplicationForm form, BindingResult binding, Model model,
            RedirectAttributes redirect) {
        ApplicationViews.Detail detail = queries.ownDetail(principal.actor(), id);
        if (binding.hasErrors()) {
            model.addAttribute("businessError", "Some fields could not be read. Check the highlighted fields.");
            return formView(principal, model, detail);
        }
        try {
            commands.update(principal.actor(), id, form.toDetails(), form.getExpectedVersion());
            redirect.addFlashAttribute("flashSuccess", "Application " + detail.referenceNo()
                    + " was updated. Your approving manager has been notified.");
            return "redirect:/employee/applications/" + id;
        } catch (BusinessException e) {
            FormErrors.apply(e, binding, model);
            return formView(principal, model, detail);
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal UptrailUserPrincipal principal, @PathVariable Long id,
            @RequestParam(required = false) Long expectedVersion, RedirectAttributes redirect) {
        return act(id, redirect, null, null, "The application was deleted and its reservation released.",
                () -> commands.delete(principal.actor(), id, expectedVersion));
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@AuthenticationPrincipal UptrailUserPrincipal principal, @PathVariable Long id,
            @RequestParam(required = false) Long expectedVersion, @RequestParam(required = false) String reason,
            RedirectAttributes redirect) {
        return act(id, redirect, "cancelDraft", reason,
                "The course was cancelled and the approved days and budget released.",
                () -> commands.cancel(principal.actor(), id, expectedVersion, reason));
    }

    @PostMapping("/{id}/complete")
    public String complete(@AuthenticationPrincipal UptrailUserPrincipal principal, @PathVariable Long id,
            @RequestParam(required = false) Long expectedVersion,
            @RequestParam(required = false) String experienceComments, RedirectAttributes redirect) {
        return act(id, redirect, "completeDraft", experienceComments,
                "Attendance confirmed. The course is now completed.",
                () -> commands.complete(principal.actor(), id, expectedVersion, experienceComments));
    }

    /** Runs a state change and redirects back to the detail page with the outcome; typed text is kept. */
    private static String act(Long id, RedirectAttributes redirect, String draftName, String draft, String success,
            Runnable action) {
        try {
            action.run();
            redirect.addFlashAttribute("flashSuccess", success);
        } catch (BusinessException e) {
            if (e instanceof com.uptrail.shared.error.NotFoundException) {
                throw e;
            }
            redirect.addFlashAttribute("flashError", e.getMessage());
            if (draftName != null) {
                redirect.addFlashAttribute(draftName, draft);
            }
        }
        return "redirect:/employee/applications/" + id;
    }

    String formView(UptrailUserPrincipal principal, Model model, ApplicationViews.Detail editing) {
        model.addAttribute("editing", editing);
        model.addAttribute("categories", catalogue.categories());
        model.addAttribute("providers", catalogue.activeProviderNames());
        model.addAttribute("today", clock.today());
        model.addAttribute("currentYear", clock.currentYear());
        model.addAttribute("approverName",
                directory.approverOf(principal.getEmployeeId()).map(directory::nameOf).orElse(null));
        return "employee/applications/form";
    }
}
