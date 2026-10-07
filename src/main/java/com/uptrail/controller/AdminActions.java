package com.uptrail.controller;

import java.util.function.Supplier;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.error.NotFoundException;

/**
 * Runs an administration command from a POST and redirects back with the outcome (post/redirect/get).
 * Business rule failures are shown as a message; a missing record is a 404.
 */
final class AdminActions {

    private AdminActions() {
    }

    static String run(RedirectAttributes redirect, String target, Supplier<String> command) {
        try {
            String success = command.get();
            if (success != null) {
                redirect.addFlashAttribute("flashSuccess", success);
            }
        } catch (NotFoundException e) {
            throw e;
        } catch (BusinessException e) {
            String details = e.fieldErrors().isEmpty() ? "" : " " + String.join(" ", e.fieldErrors().values());
            redirect.addFlashAttribute("flashError",
                    e.getMessage().equals("Some fields need attention.") ? details.strip() : e.getMessage());
        }
        return "redirect:" + target;
    }
}
