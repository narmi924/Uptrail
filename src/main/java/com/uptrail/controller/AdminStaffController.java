package com.uptrail.controller;

import com.uptrail.model.User;

import java.util.EnumSet;
import java.util.Set;

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

import com.uptrail.service.RoutingAdminService;
import com.uptrail.service.StaffAdminService;
import com.uptrail.model.Role;
import com.uptrail.model.Designation;
import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.web.Paging;

/**
 * Staff and role administration pages.
 */
@Controller
@RequestMapping("/admin/staff")
public class AdminStaffController {

    private final StaffAdminService staff;
    private final RoutingAdminService routing;

    public AdminStaffController(StaffAdminService staff, RoutingAdminService routing) {
        this.staff = staff;
        this.routing = routing;
    }

    @InitBinder("form")
    void allowedFields(WebDataBinder binder) {
        binder.setAllowedFields("staffId", "name", "email", "department", "designation", "username", "password",
                "roles", "approverId", "openCurrentYearAccount");
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            HttpServletRequest request, Model model) {
        Boolean active = "inactive".equals(status) ? Boolean.FALSE : "all".equals(status) ? null : Boolean.TRUE;
        model.addAttribute("view", Paging.view(staff.search(q, active, Paging.request(page, size)), request));
        model.addAttribute("q", q);
        model.addAttribute("status", status == null ? "active" : status);
        return "admin/staff/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new StaffForm());
        return formView(model);
    }

    @PostMapping
    public String create(@ModelAttribute(value = "user", binding = false) User user, @ModelAttribute("form") StaffForm form,
            BindingResult binding, Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("businessError", "Some fields could not be read.");
            return formView(model);
        }
        try {
            Long id = staff.create(user, form.toCommand());
            redirect.addFlashAttribute("flashSuccess", form.getName() + " was added.");
            return "redirect:/admin/staff/" + id;
        } catch (BusinessException e) {
            e.fieldErrors().forEach((field, message) -> binding.rejectValue(field, e.code().name(), message));
            model.addAttribute("businessError", e.getMessage());
            return formView(model);
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("detail", staff.detail(id));
        model.addAttribute("designations", Designation.values());
        model.addAttribute("roles", Role.values());
        model.addAttribute("managers", routing.managers());
        return "admin/staff/detail";
    }

    @PostMapping("/{id}/profile")
    public String profile(@ModelAttribute(value = "user", binding = false) User user, @PathVariable Long id,
            @RequestParam(required = false) String name, @RequestParam(required = false) String email,
            @RequestParam(required = false) String department, @RequestParam(required = false) Designation designation,
            @RequestParam long version, RedirectAttributes redirect) {
        return AdminActions.run(redirect, "/admin/staff/" + id, () -> {
            staff.updateProfile(user, id, new StaffAdminService.Profile(name, email, department,
                    designation), version);
            return "Profile saved.";
        });
    }

    @PostMapping("/{id}/roles")
    public String roles(@ModelAttribute(value = "user", binding = false) User user, @PathVariable Long id,
            @RequestParam(required = false) Set<Role> roles, RedirectAttributes redirect) {
        return AdminActions.run(redirect, "/admin/staff/" + id, () -> {
            staff.changeRoles(user, id, roles == null ? EnumSet.noneOf(Role.class) : roles);
            return "Roles saved. The person's open sessions were ended so the new roles apply at next sign-in.";
        });
    }

    @PostMapping("/{id}/password")
    public String password(@ModelAttribute(value = "user", binding = false) User user, @PathVariable Long id,
            @RequestParam(required = false) String password, RedirectAttributes redirect) {
        return AdminActions.run(redirect, "/admin/staff/" + id, () -> {
            staff.resetPassword(user, id, password);
            return "Password reset. Give the new password to the person through a separate channel.";
        });
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@ModelAttribute(value = "user", binding = false) User user, @PathVariable Long id,
            RedirectAttributes redirect) {
        return AdminActions.run(redirect, "/admin/staff/" + id, () -> {
            staff.deactivate(user, id);
            return "Deactivated. The record and its history are kept; sign-in is disabled.";
        });
    }

    @PostMapping("/{id}/reactivate")
    public String reactivate(@ModelAttribute(value = "user", binding = false) User user, @PathVariable Long id,
            RedirectAttributes redirect) {
        return AdminActions.run(redirect, "/admin/staff/" + id, () -> {
            staff.reactivate(user, id);
            return "Reactivated. Check the approval routing and training accounts.";
        });
    }

    @PostMapping("/{id}/delete")
    public String delete(@ModelAttribute(value = "user", binding = false) User user, @PathVariable Long id,
            RedirectAttributes redirect) {
        try {
            staff.delete(user, id);
            redirect.addFlashAttribute("flashSuccess", "The record was deleted.");
            return "redirect:/admin/staff";
        } catch (com.uptrail.shared.error.NotFoundException e) {
            throw e;
        } catch (BusinessException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/admin/staff/" + id;
        }
    }

    private String formView(Model model) {
        model.addAttribute("designations", Designation.values());
        model.addAttribute("roles", Role.values());
        model.addAttribute("managers", routing.managers());
        return "admin/staff/new";
    }
}
