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

import com.uptrail.service.RoutingAdminService;
import com.uptrail.shared.web.Paging;

@Controller
public class AdminRoutingController {

    private final RoutingAdminService routing;

    public AdminRoutingController(RoutingAdminService routing) {
        this.routing = routing;
    }

    @GetMapping("/admin/routing")
    public String list(@RequestParam(required = false) String q, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, HttpServletRequest request, Model model) {
        model.addAttribute("view", Paging.view(routing.list(q, Paging.request(page, size)), request));
        model.addAttribute("managers", routing.managers());
        model.addAttribute("q", q);
        return "admin/routing";
    }

    @PostMapping("/admin/routing/{employeeId}")
    public String assign(@ModelAttribute(value = "user", binding = false) User user, @PathVariable Long employeeId,
            @RequestParam(required = false) Long managerId, @RequestParam(defaultValue = "false") boolean reassignPending,
            @RequestParam(required = false) String returnTo, RedirectAttributes redirect) {
        String target = returnTo != null && returnTo.startsWith("/admin/") ? returnTo : "/admin/routing";
        return AdminActions.run(redirect, target, () -> {
            int moved = routing.assign(user, employeeId, managerId, reassignPending);
            if (managerId == null) {
                return "Approver removed. This person cannot submit applications until a new approver is set.";
            }
            return "Approver saved." + (moved > 0 ? " " + moved + " waiting item(s) moved to the new approver." : "");
        });
    }

    @PostMapping("/admin/routing/move-team")
    public String moveTeam(@ModelAttribute(value = "user", binding = false) User user,
            @RequestParam(required = false) Long fromManagerId, @RequestParam(required = false) Long toManagerId,
            RedirectAttributes redirect) {
        return AdminActions.run(redirect, "/admin/routing", () -> {
            if (fromManagerId == null || toManagerId == null) {
                throw new com.uptrail.shared.error.BusinessException(
                        com.uptrail.shared.error.ErrorCode.VALIDATION_FAILED, "Choose both managers.");
            }
            int moved = routing.moveTeam(user, fromManagerId, toManagerId);
            return moved + " staff member(s) and their waiting items were moved.";
        });
    }
}
