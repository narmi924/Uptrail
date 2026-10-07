package com.uptrail.controller;

import com.uptrail.model.User;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

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

import com.uptrail.model.ApplicationStatus;
import com.uptrail.service.CourseApplicationService;
import com.uptrail.service.CourseApplicationService.SubmitResult;
import com.uptrail.service.ApplicationQueryService;
import com.uptrail.service.ApplicationViews;
import com.uptrail.model.CategoryCode;
import com.uptrail.service.CatalogueQueryService;
import com.uptrail.service.StaffService;
import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.time.BusinessClock;
import com.uptrail.shared.web.Paging;

/**
 * User pages: personal course history, application form and application detail.
 */
@Controller
@RequestMapping("/staff/applications")
public class StaffController {

    private static final String[] FORM_FIELDS = {"catalogueId", "category", "courseTitle", "providerName",
            "startDate", "endDate", "startSession", "endSession", "courseFee", "justification", "workDissemination",
            "clientRequestId", "expectedVersion"};

    private final ApplicationQueryService queries;
    private final CourseApplicationService commands;
    private final CatalogueQueryService catalogue;
    private final StaffService directory;
    private final BusinessClock clock;

    public StaffController(ApplicationQueryService queries, CourseApplicationService commands,
            CatalogueQueryService catalogue, StaffService directory, BusinessClock clock) {
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
    public String history(@ModelAttribute(value = "user", binding = false) User user,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) CategoryCode category, @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            HttpServletRequest request, Model model) {
        int year = clock.currentYear();
        var result = queries.history(user.getUserId(), year, status, category, q, Paging.request(page, size));
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
    public String newForm(@ModelAttribute(value = "user", binding = false) User user, Model model) {
        ApplicationForm form = new ApplicationForm();
        form.setClientRequestId(UUID.randomUUID().toString());
        model.addAttribute("form", form);
        return formView(user, model, null);
    }

    @PostMapping
    public String submit(@ModelAttribute(value = "user", binding = false) User user,
            @ModelAttribute("form") ApplicationForm form, BindingResult binding, Model model,
            RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("businessError", "Some fields could not be read. Check the highlighted fields.");
            return formView(user, model, null);
        }
        try {
            SubmitResult result = commands.submit(user, form.toDetails(), form.getClientRequestId());
            redirect.addFlashAttribute("flashSuccess", result.replayed()
                    ? "This application was already submitted as " + result.referenceNo() + "."
                    : "Application " + result.referenceNo() + " was submitted. An email notification to your "
                            + "approving manager has been queued.");
            return "redirect:/staff/applications/" + result.applicationId();
        } catch (BusinessException e) {
            FormErrors.apply(e, binding, model);
            return formView(user, model, null);
        }
    }

    @GetMapping("/{id}")
    public String detail(@ModelAttribute(value = "user", binding = false) User user, @PathVariable Long id,
            Model model) {
        model.addAttribute("app", queries.ownDetail(user, id));
        return "employee/applications/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@ModelAttribute(value = "user", binding = false) User user, @PathVariable Long id, Model model,
            RedirectAttributes redirect) {
        ApplicationViews.Detail detail = queries.ownDetail(user, id);
        if (!detail.actions().canEdit()) {
            redirect.addFlashAttribute("flashError", "Application " + detail.referenceNo() + " is "
                    + detail.status().label().toLowerCase() + " and can no longer be edited.");
            return "redirect:/staff/applications/" + id;
        }
        model.addAttribute("form", ApplicationForm.from(detail));
        return formView(user, model, detail);
    }

    @PostMapping("/{id}/update")
    public String update(@ModelAttribute(value = "user", binding = false) User user, @PathVariable Long id,
            @ModelAttribute("form") ApplicationForm form, BindingResult binding, Model model,
            RedirectAttributes redirect) {
        ApplicationViews.Detail detail = queries.ownDetail(user, id);
        if (binding.hasErrors()) {
            model.addAttribute("businessError", "Some fields could not be read. Check the highlighted fields.");
            return formView(user, model, detail);
        }
        try {
            commands.update(user, id, form.toDetails(), form.getExpectedVersion());
            redirect.addFlashAttribute("flashSuccess", "Application " + detail.referenceNo()
                    + " was updated. Your approving manager has been notified.");
            return "redirect:/staff/applications/" + id;
        } catch (BusinessException e) {
            FormErrors.apply(e, binding, model);
            return formView(user, model, detail);
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@ModelAttribute(value = "user", binding = false) User user, @PathVariable Long id,
            @RequestParam(required = false) Long expectedVersion, RedirectAttributes redirect) {
        return act(id, redirect, null, null, "The application was deleted and its reservation released.",
                () -> commands.delete(user, id, expectedVersion));
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@ModelAttribute(value = "user", binding = false) User user, @PathVariable Long id,
            @RequestParam(required = false) Long expectedVersion, @RequestParam(required = false) String reason,
            RedirectAttributes redirect) {
        return act(id, redirect, "cancelDraft", reason,
                "The course was cancelled and the approved days and budget released.",
                () -> commands.cancel(user, id, expectedVersion, reason));
    }

    @PostMapping("/{id}/complete")
    public String complete(@ModelAttribute(value = "user", binding = false) User user, @PathVariable Long id,
            @RequestParam(required = false) Long expectedVersion,
            @RequestParam(required = false) String experienceComments, RedirectAttributes redirect) {
        return act(id, redirect, "completeDraft", experienceComments,
                "Attendance confirmed. The course is now completed.",
                () -> commands.complete(user, id, expectedVersion, experienceComments));
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
        return "redirect:/staff/applications/" + id;
    }

    String formView(User user, Model model, ApplicationViews.Detail editing) {
        model.addAttribute("editing", editing);
        model.addAttribute("categories", catalogue.categories());
        model.addAttribute("providers", catalogue.activeProviderNames());
        model.addAttribute("today", clock.today());
        model.addAttribute("currentYear", clock.currentYear());
        model.addAttribute("approverName",
                directory.approverOf(user.getUserId()).map(directory::nameOf).orElse(null));
        return "employee/applications/form";
    }
}
