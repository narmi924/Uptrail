package com.uptrail.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uptrail.model.User;
import com.uptrail.model.Role;
import com.uptrail.repo.ApprovalHierarchyRepo;
import com.uptrail.shared.error.NotFoundException;

/**
 * Data-scope rules shared by pages, APIs, CSV exports and downloads. Every failed check raises
 * {@link NotFoundException}, so a record outside the caller's scope looks exactly like a missing one.
 */
@Service
@Transactional(readOnly = true)
public class AccessScopePolicy {

    private final ApprovalHierarchyRepo assignments;

    public AccessScopePolicy(ApprovalHierarchyRepo assignments) {
        this.assignments = assignments;
    }

    public void requireOwner(User actor, Long ownerEmployeeId) {
        if (!Objects.equals(actor.getUserId(), ownerEmployeeId)) {
            throw new NotFoundException();
        }
    }

    public boolean isCurrentDirectManager(User actor, Long employeeId) {
        return actor.hasRole(Role.MANAGER)
                && assignments.existsByEmployeeIdAndManagerId(employeeId, actor.getUserId());
    }

    public void requireCurrentDirectManager(User actor, Long employeeId) {
        if (!isCurrentDirectManager(actor, employeeId)) {
            throw new NotFoundException();
        }
    }

    /**
     * A manager may read a subordinate's record while being the current direct manager, the approver
     * assigned to that record, or the person who decided it (decisions stay visible after reorganisation).
     */
    public boolean canManagerView(User actor, Long ownerEmployeeId, Long assignedApproverId, Long reviewerId) {
        if (!actor.hasRole(Role.MANAGER) || Objects.equals(actor.getUserId(), ownerEmployeeId)) {
            return false;
        }
        return Objects.equals(actor.getUserId(), assignedApproverId)
                || Objects.equals(actor.getUserId(), reviewerId)
                || assignments.existsByEmployeeIdAndManagerId(ownerEmployeeId, actor.getUserId());
    }
}
