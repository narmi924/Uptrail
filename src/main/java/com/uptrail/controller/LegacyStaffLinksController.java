package com.uptrail.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Read-only aliases for links in pre-V6 emails. The destination keeps its ownership checks. */
@Controller
public class LegacyStaffLinksController {

    @GetMapping("/employee/applications/{id}")
    public String application(@PathVariable Long id) {
        return "redirect:/staff/applications/" + id;
    }

    @GetMapping("/employee/claims/{id}")
    public String claim(@PathVariable Long id) {
        return "redirect:/staff/claims/" + id;
    }
}
