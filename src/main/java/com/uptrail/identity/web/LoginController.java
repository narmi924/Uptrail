package com.uptrail.identity.web;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The two sign-in pages. Spring Security processes the POST of each form. In demo mode
 * ({@code uptrail.demo.enabled=true}) the pages also list sample accounts and their shared password, so
 * that visitors of a public demo can try each role.
 */
@Controller
public class LoginController {

    public record DemoAccount(String username, String description) {
    }

    public record Demo(String password, List<DemoAccount> accounts) {
    }

    private static final List<DemoAccount> STAFF_ACCOUNTS = List.of(
            new DemoAccount("siti", "Employee with a reimbursed fee claim"),
            new DemoAccount("farid", "Employee with a course fee still to claim"),
            new DemoAccount("daniel", "Manager with applications and claims to decide"),
            new DemoAccount("priya", "Manager of a second team"));

    private static final List<DemoAccount> ADMIN_ACCOUNTS = List.of(
            new DemoAccount("alex", "Administrator"),
            new DemoAccount("nurul", "Administrator who is also an employee"));

    private final boolean demo;
    private final String samplePassword;

    public LoginController(@Value("${uptrail.demo.enabled:false}") boolean demo,
            @Value("${uptrail.sample-data.password}") String samplePassword) {
        this.demo = demo;
        this.samplePassword = samplePassword;
    }

    @GetMapping("/login")
    public String staffLogin(@RequestParam(required = false) String next, Model model) {
        model.addAttribute("next", EntryPoint.STAFF.isSafeRedirect(next) ? next : null);
        addDemo(model, STAFF_ACCOUNTS);
        return "auth/login";
    }

    @GetMapping("/admin/login")
    public String adminLogin(@RequestParam(required = false) String next, Model model) {
        model.addAttribute("next", EntryPoint.ADMIN.isSafeRedirect(next) ? next : null);
        addDemo(model, ADMIN_ACCOUNTS);
        return "auth/admin-login";
    }

    private void addDemo(Model model, List<DemoAccount> accounts) {
        if (demo) {
            model.addAttribute("demo", new Demo(samplePassword, accounts));
        }
    }
}
