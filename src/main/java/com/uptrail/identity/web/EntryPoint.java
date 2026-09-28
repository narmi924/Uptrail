package com.uptrail.identity.web;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.uptrail.identity.domain.Role;

/**
 * The two sign-in entry points required by the course brief. A session is bound to the entry it was
 * created through, which keeps the staff and administration workspaces separate.
 */
public enum EntryPoint {

    STAFF("ENTRY_STAFF", EnumSet.of(Role.EMPLOYEE, Role.MANAGER), "/employee/dashboard",
            List.of("/employee/", "/manager/", "/calendar")),
    ADMIN("ENTRY_ADMIN", EnumSet.of(Role.ADMIN), "/admin/dashboard", List.of("/admin/", "/calendar"));

    private final String authority;
    private final Set<Role> acceptedRoles;
    private final String home;
    private final List<String> redirectPrefixes;

    EntryPoint(String authority, Set<Role> acceptedRoles, String home, List<String> redirectPrefixes) {
        this.authority = authority;
        this.acceptedRoles = acceptedRoles;
        this.home = home;
        this.redirectPrefixes = redirectPrefixes;
    }

    public String authority() {
        return authority;
    }

    public boolean accepts(Set<Role> roles) {
        return roles.stream().anyMatch(acceptedRoles::contains);
    }

    public String home() {
        return home;
    }

    /** Only same-site paths inside this workspace may be used as the post-login destination. */
    public boolean isSafeRedirect(String target) {
        if (target == null || target.isBlank() || !target.startsWith("/") || target.startsWith("//")
                || target.contains("\\") || target.contains("://") || target.contains("\r")
                || target.contains("\n")) {
            return false;
        }
        return redirectPrefixes.stream().anyMatch(target::startsWith);
    }
}
