package com.uptrail.identity.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The two sign-in pages. Spring Security processes the POST of each form.
 */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String staffLogin(@RequestParam(required = false) String next, Model model) {
        model.addAttribute("next", EntryPoint.STAFF.isSafeRedirect(next) ? next : null);
        return "auth/login";
    }

    @GetMapping("/admin/login")
    public String adminLogin(@RequestParam(required = false) String next, Model model) {
        model.addAttribute("next", EntryPoint.ADMIN.isSafeRedirect(next) ? next : null);
        return "auth/admin-login";
    }
}
