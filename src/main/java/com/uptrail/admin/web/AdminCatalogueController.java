package com.uptrail.admin.web;

import java.math.BigDecimal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.uptrail.admin.service.CatalogueAdminService;
import com.uptrail.admin.service.CatalogueAdminService.CourseInput;
import com.uptrail.catalogue.domain.CategoryCode;
import com.uptrail.identity.service.UptrailUserPrincipal;

@Controller
public class AdminCatalogueController {

    private final CatalogueAdminService catalogue;

    public AdminCatalogueController(CatalogueAdminService catalogue) {
        this.catalogue = catalogue;
    }

    @GetMapping("/admin/catalogue")
    public String page(@RequestParam(defaultValue = "courses") String tab, Model model) {
        String selected = switch (tab) {
            case "providers", "categories" -> tab;
            default -> "courses";
        };
        model.addAttribute("tab", selected);
        model.addAttribute("categories", catalogue.categories());
        model.addAttribute("providers", catalogue.providers());
        model.addAttribute("courses", catalogue.courses());
        model.addAttribute("categoryCodes", CategoryCode.values());
        return "admin/catalogue";
    }

    @PostMapping("/admin/catalogue/categories/{code}")
    public String category(@AuthenticationPrincipal UptrailUserPrincipal principal, @PathVariable CategoryCode code,
            @RequestParam(required = false) String displayName, @RequestParam(required = false) String description,
            RedirectAttributes redirect) {
        return AdminActions.run(redirect, "/admin/catalogue?tab=categories", () -> {
            catalogue.updateCategory(principal.actor(), code, displayName, description);
            return "Category text saved.";
        });
    }

    @PostMapping("/admin/catalogue/providers")
    public String addProvider(@AuthenticationPrincipal UptrailUserPrincipal principal,
            @RequestParam(required = false) String name, RedirectAttributes redirect) {
        return AdminActions.run(redirect, "/admin/catalogue?tab=providers", () -> {
            catalogue.addProvider(principal.actor(), name);
            return "Provider added.";
        });
    }

    @PostMapping("/admin/catalogue/providers/{id}")
    public String updateProvider(@AuthenticationPrincipal UptrailUserPrincipal principal, @PathVariable Long id,
            @RequestParam(required = false) String name, @RequestParam(defaultValue = "false") boolean active,
            RedirectAttributes redirect) {
        return AdminActions.run(redirect, "/admin/catalogue?tab=providers", () -> {
            catalogue.updateProvider(principal.actor(), id, name, active);
            return "Provider saved.";
        });
    }

    @PostMapping("/admin/catalogue/courses")
    public String addCourse(@AuthenticationPrincipal UptrailUserPrincipal principal,
            @RequestParam(required = false) CategoryCode category, @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) String title, @RequestParam(required = false) BigDecimal defaultFee,
            @RequestParam(required = false) String description, RedirectAttributes redirect) {
        return AdminActions.run(redirect, "/admin/catalogue?tab=courses", () -> {
            catalogue.addCourse(principal.actor(), new CourseInput(category, providerId, title, defaultFee, description));
            return "Course added to the catalogue.";
        });
    }

    @PostMapping("/admin/catalogue/courses/{id}")
    public String updateCourse(@AuthenticationPrincipal UptrailUserPrincipal principal, @PathVariable Long id,
            @RequestParam(required = false) CategoryCode category, @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) String title, @RequestParam(required = false) BigDecimal defaultFee,
            @RequestParam(required = false) String description, @RequestParam(defaultValue = "false") boolean active,
            RedirectAttributes redirect) {
        return AdminActions.run(redirect, "/admin/catalogue?tab=courses", () -> {
            catalogue.updateCourse(principal.actor(), id, new CourseInput(category, providerId, title, defaultFee,
                    description), active);
            return "Course saved. Submitted applications keep their own copy of the details.";
        });
    }
}
