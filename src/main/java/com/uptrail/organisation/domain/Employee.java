package com.uptrail.organisation.domain;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A person working at the institute. Login credentials and security roles live in the identity module;
 * an employee record is also the lock anchor that serialises all training writes for that person.
 */
@Entity
@Table(name = "employee")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "staff_no", nullable = false, length = 30)
    private String staffNo;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "department", nullable = false, length = 100)
    private String department;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "designation_code", nullable = false, length = 40)
    private Designation designation;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Employee() {
    }

    public static Employee create(String staffNo, String fullName, String email, String department,
            Designation designation, Instant now) {
        Employee employee = new Employee();
        employee.staffNo = Objects.requireNonNull(staffNo);
        employee.fullName = Objects.requireNonNull(fullName);
        employee.email = Objects.requireNonNull(email);
        employee.department = Objects.requireNonNull(department);
        employee.designation = Objects.requireNonNull(designation);
        employee.active = true;
        employee.createdAt = now;
        employee.updatedAt = now;
        return employee;
    }

    public void updateProfile(String fullName, String email, String department, Designation designation,
            Instant now) {
        this.fullName = Objects.requireNonNull(fullName);
        this.email = Objects.requireNonNull(email);
        this.department = Objects.requireNonNull(department);
        this.designation = Objects.requireNonNull(designation);
        this.updatedAt = now;
    }

    public void deactivate(Instant now) {
        this.active = false;
        this.updatedAt = now;
    }

    public void reactivate(Instant now) {
        this.active = true;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getStaffNo() {
        return staffNo;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getDepartment() {
        return department;
    }

    public Designation getDesignation() {
        return designation;
    }

    public boolean isActive() {
        return active;
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
