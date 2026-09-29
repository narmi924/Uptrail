package com.uptrail.identity.domain;

import java.util.Objects;
import java.util.Set;

/**
 * The authenticated person performing an operation. Services receive the actor explicitly and never
 * trust identity values posted by the browser.
 */
public record Actor(Long employeeId, String displayName, Set<Role> roles) {

    public Actor {
        Objects.requireNonNull(employeeId);
        roles = Set.copyOf(roles);
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
}
