package com.uptrail.service;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.uptrail.model.User;
import com.uptrail.model.Role;

/**
 * Security principal. Equality is by username so that the session registry can find and expire every
 * session of a user after deactivation or a role change.
 */
public final class UptrailUserPrincipal implements UserDetails, CredentialsContainer {

    private final Long userId;
    private final String username;
    private String passwordHash;
    private final String displayName;
    private final Set<Role> roles;
    private final boolean enabled;

    public UptrailUserPrincipal(Long userId, String username, String passwordHash,
            String displayName, Set<Role> roles, boolean enabled) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.roles = roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles);
        this.enabled = enabled;
    }

    public User actor() {
        return User.identity(userId, displayName, roles);
    }

    public Long getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Set<Role> getRoles() {
        return EnumSet.copyOf(roles.isEmpty() ? EnumSet.noneOf(Role.class) : roles);
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream().map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role.authority())).toList();
    }

    public List<GrantedAuthority> authoritiesWith(String extraAuthority) {
        List<GrantedAuthority> authorities = new java.util.ArrayList<>(getAuthorities());
        authorities.add(new SimpleGrantedAuthority(extraAuthority));
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof UptrailUserPrincipal principal && username.equals(principal.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username);
    }

    @Override
    public String toString() {
        return "UptrailUserPrincipal[" + username + "]";
    }
}
