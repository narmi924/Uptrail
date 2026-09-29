package com.uptrail.organisation.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * The single direct approver of an employee. Submissions copy the approver onto the application, so a
 * later routing change never rewrites decisions that were already taken.
 */
@Entity
@Table(name = "approval_assignment")
public class ApprovalAssignment {

    @Id
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "manager_id", nullable = false)
    private Long managerId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ApprovalAssignment() {
    }

    public static ApprovalAssignment assign(Long employeeId, Long managerId, Instant now) {
        if (employeeId.equals(managerId)) {
            throw new IllegalArgumentException("An employee cannot approve their own applications");
        }
        ApprovalAssignment assignment = new ApprovalAssignment();
        assignment.employeeId = employeeId;
        assignment.managerId = managerId;
        assignment.updatedAt = now;
        return assignment;
    }

    public void changeManager(Long newManagerId, Instant now) {
        if (employeeId.equals(newManagerId)) {
            throw new IllegalArgumentException("An employee cannot approve their own applications");
        }
        this.managerId = newManagerId;
        this.updatedAt = now;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public Long getManagerId() {
        return managerId;
    }

    public long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
