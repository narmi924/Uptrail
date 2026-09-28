package com.uptrail.application.web;

import org.springframework.beans.NotReadablePropertyException;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;

import com.uptrail.shared.error.BusinessException;

/**
 * Copies a business rule failure onto the form: field messages next to their fields and the main message
 * in the summary at the top. The entered values stay in the form.
 */
final class FormErrors {

    private FormErrors() {
    }

    static void apply(BusinessException e, BindingResult binding, Model model) {
        e.fieldErrors().forEach((field, message) -> {
            try {
                binding.rejectValue(field, e.code().name(), message);
            } catch (NotReadablePropertyException ignored) {
                binding.reject(e.code().name(), message);
            }
        });
        model.addAttribute("businessError", e.getMessage());
        model.addAttribute("businessErrorCode", e.code().name());
    }
}
