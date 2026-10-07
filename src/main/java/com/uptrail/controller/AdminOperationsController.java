package com.uptrail.controller;

import org.springframework.web.bind.annotation.ModelAttribute;

import com.uptrail.model.User;

import java.time.LocalDate;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.uptrail.service.OperationsService;
import com.uptrail.service.OperationsService.AuditFilter;
import com.uptrail.model.AggregateType;
import com.uptrail.model.OutboxStatus;
import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.error.NotFoundException;
import com.uptrail.shared.web.Paging;

/**
 * Operations: audit trail search, email outbox with retry, and the ledger consistency check.
 */
@Controller
public class AdminOperationsController {

    private final OperationsService operations;

    public AdminOperationsController(OperationsService operations) {
        this.operations = operations;
    }

    @GetMapping("/admin/operations")
    public String page(@RequestParam(defaultValue = "audit") String tab,
            @RequestParam(required = false) AggregateType type, @RequestParam(required = false) String key,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) OutboxStatus status, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, HttpServletRequest request, Model model) {
        String selected = switch (tab) {
            case "outbox", "ledger" -> tab;
            default -> "audit";
        };
        model.addAttribute("tab", selected);
        model.addAttribute("outboxCounts", operations.outboxCounts());
        switch (selected) {
            case "outbox" -> {
                model.addAttribute("status", status);
                model.addAttribute("statuses", OutboxStatus.values());
                model.addAttribute("view", Paging.view(operations.outbox(status, Paging.request(page, size)), request));
            }
            case "ledger" -> model.addAttribute("ledger", operations.ledgerCheck());
            default -> {
                AuditFilter filter = operations.defaultFilter(type, key, eventType, from, to);
                model.addAttribute("filter", filter);
                model.addAttribute("types", operations.aggregateTypes());
                try {
                    model.addAttribute("view",
                            Paging.view(operations.audit(filter, Paging.request(page, size)), request));
                } catch (BusinessException e) {
                    model.addAttribute("flashError", e.getMessage());
                }
            }
        }
        return "admin/operations";
    }

    @PostMapping("/admin/outbox/{id}/retry")
    public String retry(@ModelAttribute(value = "user", binding = false) User user, @PathVariable Long id,
            RedirectAttributes redirect) {
        try {
            operations.retry(user, id);
            redirect.addFlashAttribute("flashSuccess", "The email is queued again and will be sent by the next worker run.");
        } catch (NotFoundException e) {
            throw e;
        } catch (BusinessException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/admin/operations?tab=outbox&status=FAILED";
    }
}
