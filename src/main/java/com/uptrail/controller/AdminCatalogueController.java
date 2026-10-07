package com.uptrail.controller;

import org.springframework.web.bind.annotation.ModelAttribute;

import com.uptrail.model.User;

import java.math.BigDecimal;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.uptrail.service.CatalogueAdminService;
import com.uptrail.service.CatalogueAdminService.CourseInput;
import com.uptrail.model.CategoryCode;
import com.uptrail.shared.web.Paging;

@Controller
public class AdminCatalogueController {

    private final CatalogueAdminService catalogue;

    public AdminCatalogueController(CatalogueAdminService catalogue) {
        this.catalogue = catalogue;
    }

    @GetMapping("/admin/catalogue")
    public String page(@RequestParam(defaultValue = "courses") String tab, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, HttpServletRequest request, Model model) {
        String selected = switch (tab) {
            case "providers", "categories" -> tab;
            default -> "courses";
        };
        List<CatalogueAdminService.ProviderRow> providers = catalogue.providers();
        model.addAttribute("tab", selected);
        model.addAttribute("categories", catalogue.categories());
        model.addAttribute("providers", providers);
        model.addAttribute("categoryCodes", CategoryCode.values());
        if (selected.equals("courses")) {
            model.addAttribute("courseView",
                    Paging.view(Paging.slice(catalogue.courses(), Paging.request(page, size)), request));
        } else if (selected.equals("providers")) {
            model.addAttribute("providerView", Paging.view(Paging.slice(providers, Paging.request(page, size)), request));
        }
        return "admin/catalogue";
    }

    @PostMapping("/admin/catalogue/categories/{code}")
    public String category(@ModelAttribute(value = "user", binding = false) User user, @PathVariable CategoryCode code,
            @RequestParam(required = false) String displayName, @RequestParam(required = false) String description,
            RedirectAttributes redirect) {
        return AdminActions.run(redirect, "/admin/catalogue?tab=categories", () -> {
            catalogue.updateCategory(user, code, displayName, description);
            return "Category text saved.";
        });
    }

    @PostMapping("/admin/catalogue/providers")
    public String addProvider(@ModelAttribute(value = "user", binding = false) User user,
            @RequestParam(required = false) String name, RedirectAttributes redirect) {
        return AdminActions.run(redirect, "/admin/catalogue?tab=providers", () -> {
            catalogue.addProvider(user, name);
            return "Provider added.";
        });
    }

    @PostMapping("/admin/catalogue/providers/{id}")
    public String updateProvider(@ModelAttribute(value = "user", binding = false) User user, @PathVariable Long id,
            @RequestParam(required = false) String name, @RequestParam(defaultValue = "false") boolean active,
            RedirectAttributes redirect) {
        return AdminActions.run(redirect, "/admin/catalogue?tab=providers", () -> {
            catalogue.updateProvider(user, id, name, active);
            return "Provider saved.";
        });
    }

    @PostMapping("/admin/catalogue/courses")
    public String addCourse(@ModelAttribute(value = "user", binding = false) User user,
            @RequestParam(required = false) CategoryCode category, @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) String title, @RequestParam(required = false) BigDecimal defaultFee,
            @RequestParam(required = false) String description, RedirectAttributes redirect) {
        return AdminActions.run(redirect, "/admin/catalogue?tab=courses", () -> {
            catalogue.addCourse(user, new CourseInput(category, providerId, title, defaultFee, description));
            return "Course added to the catalogue.";
        });
    }

    @PostMapping("/admin/catalogue/courses/{id}")
    public String updateCourse(@ModelAttribute(value = "user", binding = false) User user, @PathVariable Long id,
            @RequestParam(required = false) CategoryCode category, @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) String title, @RequestParam(required = false) BigDecimal defaultFee,
            @RequestParam(required = false) String description, @RequestParam(defaultValue = "false") boolean active,
            RedirectAttributes redirect) {
        return AdminActions.run(redirect, "/admin/catalogue?tab=courses", () -> {
            catalogue.updateCourse(user, id, new CourseInput(category, providerId, title, defaultFee,
                    description), active);
            return "Course saved. Submitted applications keep their own copy of the details.";
        });
    }
}
