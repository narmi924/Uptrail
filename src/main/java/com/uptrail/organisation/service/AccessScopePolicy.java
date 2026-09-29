package com.uptrail.organisation.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uptrail.identity.domain.Actor;
import com.uptrail.identity.domain.Role;
import com.uptrail.organisation.repository.ApprovalAssignmentRepository;
import com.uptrail.shared.error.NotFoundException;

/**
 * Data-scope rules shared by pages, APIs, CSV exports and downloads. Every failed check raises
 * {@link NotFoundException}, so a record outside the caller's scope looks exactly like a missing one.
 */
@Service
@Transactional(readOnly = true)
public class AccessScopePolicy {

    private final ApprovalAssignmentRepository assignments;

    public AccessScopePolicy(ApprovalAssignmentRepository assignments) {
        this.assignments = assignments;
    }

    public void requireOwner(Actor actor, Long ownerEmployeeId) {
        if (!Objects.equals(actor.employeeId(), ownerEmployeeId)) {
            throw new NotFoundException();
        }
    }

    public boolean isCurrentDirectManager(Actor actor, Long employeeId) {
        return actor.hasRole(Role.MANAGER)
                && assignments.existsByEmployeeIdAndManagerId(employeeId, actor.employeeId());
    }

    public void requireCurrentDirectManager(Actor actor, Long employeeId) {
        if (!isCurrentDirectManager(actor, employeeId)) {
            throw new NotFoundException();
        }
    }

    /**
     * A manager may read a subordinate's record while being the current direct manager, the approver
     * assigned to that record, or the person who decided it (decisions stay visible after reorganisation).
     */
    public boolean canManagerView(Actor actor, Long ownerEmployeeId, Long assignedApproverId, Long reviewerId) {
        if (!actor.hasRole(Role.MANAGER) || Objects.equals(actor.employeeId(), ownerEmployeeId)) {
            return false;
        }
        return Objects.equals(actor.employeeId(), assignedApproverId)
                || Objects.equals(actor.employeeId(), reviewerId)
                || assignments.existsByEmployeeIdAndManagerId(ownerEmployeeId, actor.employeeId());
    }
}
