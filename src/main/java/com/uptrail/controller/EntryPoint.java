package com.uptrail.controller;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.uptrail.model.Role;

/**
 * The two sign-in entry points (staff and administration). A session is bound to the entry it was
 * created through, which keeps the staff and administration workspaces separate.
 */
public enum EntryPoint {

    STAFF("ENTRY_STAFF", EnumSet.of(Role.STAFF, Role.MANAGER), "/staff/home",
            List.of("/staff/", "/manager/", "/calendar")),
    ADMIN("ENTRY_ADMIN", EnumSet.of(Role.ADMIN), "/admin/home", List.of("/admin/", "/calendar"));

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

    /** Keep links from pre-V6 queued and delivered emails inside the same Staff workspace. */
    public String canonicalRedirect(String target) {
        if (this == STAFF && target != null
                && target.matches("/employee/(applications|claims)/[0-9]+(?:\\?[^\\r\\n]*)?")) {
            return "/staff" + target.substring("/employee".length());
        }
        return target;
    }

    /** Only same-site paths inside this workspace may be used as the post-login destination. */
    public boolean isSafeRedirect(String target) {
        target = canonicalRedirect(target);
        if (target == null || target.isBlank() || !target.startsWith("/") || target.startsWith("//")
                || target.contains("\\") || target.contains("://") || target.contains("\r")
                || target.contains("\n")) {
            return false;
        }
        String path = target;
        return redirectPrefixes.stream().anyMatch(path::startsWith);
    }
}
