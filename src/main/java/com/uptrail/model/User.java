package com.uptrail.model;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Shared identity, sign-in and profile. Staff, Manager and Admin use the same userId. */
@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "user_type", length = 16)
public abstract class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long userId;

    @Column(name = "staff_id", nullable = false, length = 30)
    private String staffId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 100)
    private String department;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "designation_code", nullable = false, length = 40)
    private Designation designation;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "user_name", length = 80)
    private String userName;

    @JsonIgnore
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role_code", nullable = false, length = 16)
    private Set<Role> roles = new HashSet<>();

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    public static User create(String staffId, String name, String email, String department,
            Designation designation, String userName, String passwordHash, Set<Role> roles, Instant now) {
        Set<Role> permissions = validRoles(roles);
        User user = subtype(permissions);
        user.staffId = Objects.requireNonNull(staffId);
        user.name = Objects.requireNonNull(name);
        user.email = Objects.requireNonNull(email);
        user.department = Objects.requireNonNull(department);
        user.designation = Objects.requireNonNull(designation);
        user.active = true;
        user.userName = userName == null ? null : normaliseUsername(userName);
        user.passwordHash = passwordHash;
        user.enabled = userName != null && passwordHash != null;
        user.roles = permissions;
        user.createdAt = now;
        user.updatedAt = now;
        return user;
    }

    private static User subtype(Set<Role> roles) {
        if (roles.contains(Role.MANAGER)) return new Manager();
        if (roles.contains(Role.ADMIN)) return new Admin();
        return new Staff();
    }

    private static Set<Role> validRoles(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) throw new IllegalArgumentException("A user needs a role");
        EnumSet<Role> copy = EnumSet.copyOf(roles);
        if (copy.contains(Role.MANAGER)) copy.add(Role.STAFF);
        return new HashSet<>(copy);
    }

    public static String normaliseUsername(String userName) {
        return Objects.requireNonNull(userName).trim().toLowerCase(Locale.ROOT);
    }

    /** A detached session snapshot with the real subtype and no password hash. */
    public User sessionView() {
        User copy = create(staffId, name, email, department, designation, userName, null, roles, createdAt);
        copy.userId = userId;
        copy.active = active;
        copy.enabled = enabled;
        copy.version = version;
        copy.updatedAt = updatedAt;
        return copy;
    }

    public User actor() { return this; }
    /** Trusted server-side identity for test/sample commands and the security adapter. */
    public static User identity(Long userId, String name, Set<Role> roles) {
        Set<Role> permissions = validRoles(roles);
        User user = subtype(permissions);
        user.userId = Objects.requireNonNull(userId);
        user.name = name;
        user.roles = permissions;
        return user;
    }
    public Role getRole() {
        if (this instanceof Admin) return Role.ADMIN;
        if (this instanceof Manager) return Role.MANAGER;
        return Role.STAFF;
    }
    public boolean hasRole(Role role) { return roles.contains(role); }
    public Set<Role> getRoles() { return Collections.unmodifiableSet(roles); }
    public Long getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getStaffId() { return staffId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getDepartment() { return department; }
    public Designation getDesignation() { return designation; }
    public boolean isActive() { return active; }
    public boolean isEnabled() { return enabled; }
    @JsonIgnore public String getPasswordHash() { return passwordHash; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // IDs in historical ledger/notification DTOs refer to this same user, never a second account.
    public Long getId() { return userId; }
    public Long getEmployeeId() { return userId; }
    public String getUsername() { return userName; }

    public void updateProfile(String name, String email, String department, Designation designation, Instant now) {
        this.name = Objects.requireNonNull(name);
        this.email = Objects.requireNonNull(email);
        this.department = Objects.requireNonNull(department);
        this.designation = Objects.requireNonNull(designation);
        this.updatedAt = now;
    }
    public void deactivate(Instant now) { active = false; updatedAt = now; }
    public void reactivate(Instant now) { active = true; updatedAt = now; }
    public void changeRoles(Set<Role> newRoles, Instant now) { roles = validRoles(newRoles); updatedAt = now; }
    public void changePasswordHash(String hash, Instant now) { passwordHash = Objects.requireNonNull(hash); updatedAt = now; }
    public void disable(Instant now) { enabled = false; updatedAt = now; }
    public void enable(Instant now) { enabled = userName != null && passwordHash != null; updatedAt = now; }
}
