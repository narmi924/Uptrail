package com.uptrail.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Sends "/" to the dashboard of the workspace the user signed in to, or to the staff sign-in page.
 */
@Controller
public class HomeController {

    static final String ENTRY_ADMIN = "ENTRY_ADMIN";
    static final String ENTRY_STAFF = "ENTRY_STAFF";

    @GetMapping("/")
    public String home(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/employee/login";
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (ENTRY_ADMIN.equals(authority.getAuthority())) {
                return "redirect:/admin/home";
            }
            if (ENTRY_STAFF.equals(authority.getAuthority())) {
                return "redirect:/staff/home";
            }
        }
        return "redirect:/employee/login";
    }
}
