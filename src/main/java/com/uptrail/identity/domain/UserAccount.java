package com.uptrail.identity.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Login identity of an employee. The password is only ever held as an encoder hash.
 */
@Entity
@Table(name = "user_account")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "username", nullable = false, length = 80)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role_code", nullable = false, length = 16)
    private Set<Role> roles = new HashSet<>();

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAccount() {
    }

    public static UserAccount create(Long employeeId, String username, String passwordHash, Set<Role> roles,
            Instant now) {
        UserAccount account = new UserAccount();
        account.employeeId = Objects.requireNonNull(employeeId);
        account.username = normaliseUsername(username);
        account.passwordHash = Objects.requireNonNull(passwordHash);
        account.enabled = true;
        account.roles = new HashSet<>(validRoles(roles));
        account.createdAt = now;
        account.updatedAt = now;
        return account;
    }

    /** Usernames are compared case-insensitively; they are stored trimmed and lower case. */
    public static String normaliseUsername(String username) {
        return Objects.requireNonNull(username).trim().toLowerCase(Locale.ROOT);
    }

    private static Set<Role> validRoles(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("An account needs at least one role");
        }
        EnumSet<Role> copy = EnumSet.copyOf(roles);
        if (copy.contains(Role.MANAGER)) {
            // Managers apply for courses through the same employee workflow.
            copy.add(Role.EMPLOYEE);
        }
        return copy;
    }

    public void changeRoles(Set<Role> newRoles, Instant now) {
        this.roles.clear();
        this.roles.addAll(validRoles(newRoles));
        this.updatedAt = now;
    }

    public void changePasswordHash(String newHash, Instant now) {
        this.passwordHash = Objects.requireNonNull(newHash);
        this.updatedAt = now;
    }

    public void disable(Instant now) {
        this.enabled = false;
        this.updatedAt = now;
    }

    public void enable(Instant now) {
        this.enabled = true;
        this.updatedAt = now;
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public Long getId() {
        return id;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
