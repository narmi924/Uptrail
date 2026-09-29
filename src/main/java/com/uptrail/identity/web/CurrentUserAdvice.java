package com.uptrail.identity.web;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.uptrail.approval.service.ManagerApplicationService;
import com.uptrail.claim.service.ClaimQueryService;
import com.uptrail.identity.domain.Actor;
import com.uptrail.identity.domain.Role;
import com.uptrail.identity.service.UptrailUserPrincipal;

/**
 * Exposes the signed-in user to every page layout (name, roles, workspace, worklist counts) without
 * letting templates touch the security principal directly.
 */
@ControllerAdvice(annotations = Controller.class)
public class CurrentUserAdvice {

    private final ManagerApplicationService managerApplications;
    private final ClaimQueryService claims;

    public CurrentUserAdvice(ManagerApplicationService managerApplications, ClaimQueryService claims) {
        this.managerApplications = managerApplications;
        this.claims = claims;
    }

    public record CurrentUserView(Long employeeId, String displayName, String initials, String rolesLabel,
            boolean adminWorkspace, boolean employee, boolean manager, boolean admin, NavCounts counts) {
    }

    /**
     * Counts shown next to the navigation links. They are queried only when a page renders them, so
     * form posts and redirects cost nothing.
     */
    public static final class NavCounts {

        private final LongSupplier approvalsQuery;
        private final LongSupplier registrationsQuery;
        private Long approvals;
        private Long registrations;

        NavCounts(LongSupplier approvalsQuery, LongSupplier registrationsQuery) {
            this.approvalsQuery = approvalsQuery;
            this.registrationsQuery = registrationsQuery;
        }

        /** Applications and claims waiting for the signed-in manager's decision. */
        public long getApprovals() {
            if (approvals == null) {
                approvals = approvalsQuery.getAsLong();
            }
            return approvals;
        }

        /** Approved claims waiting for an administrator to register the reimbursement. */
        public long getRegistrations() {
            if (registrations == null) {
                registrations = registrationsQuery.getAsLong();
            }
            return registrations;
        }
    }

    @ModelAttribute("currentUser")
    public CurrentUserView currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UptrailUserPrincipal principal)) {
            return null;
        }
        boolean adminWorkspace = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(EntryPoint.ADMIN.authority()::equals);
        String roles = principal.getRoles().stream()
                .sorted()
                .map(Role::label)
                .collect(Collectors.joining(" · "));
        boolean manager = principal.hasRole(Role.MANAGER);
        boolean admin = principal.hasRole(Role.ADMIN);
        Actor actor = principal.actor();
        NavCounts counts = new NavCounts(
                () -> manager && !adminWorkspace
                        ? managerApplications.pendingCount(actor) + claims.pendingCount(actor) : 0,
                () -> admin && adminWorkspace ? claims.awaitingCount() : 0);
        return new CurrentUserView(principal.getEmployeeId(), principal.getDisplayName(),
                initials(principal.getDisplayName()), roles, adminWorkspace, principal.hasRole(Role.EMPLOYEE), manager,
                admin, counts);
    }

    /** First letters of the first and last word of a name, for the avatar ("Siti Rahman" gives "SR"). */
    static String initials(String name) {
        String[] words = Arrays.stream(name == null ? new String[0] : name.trim().split("\\s+"))
                .filter(w -> !w.isEmpty())
                .toArray(String[]::new);
        if (words.length == 0) {
            return "?";
        }
        String first = words[0].substring(0, 1);
        String last = words.length > 1 ? words[words.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase(Locale.ROOT);
    }
}
