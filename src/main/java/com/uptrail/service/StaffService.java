package com.uptrail.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uptrail.model.ApprovalHierarchy;
import com.uptrail.model.Designation;
import com.uptrail.model.User;
import com.uptrail.repo.ApprovalHierarchyRepo;
import com.uptrail.repo.UserRepo;
import com.uptrail.shared.error.NotFoundException;

/**
 * Read access to people and their current approver.
 */
@Service
@Transactional(readOnly = true)
public class StaffService {

    public record StaffCard(Long id, String staffId, String name, String email, String department,
            Designation designation, boolean active, Long approverId, String approverName) {
    }

    public record PersonRef(Long id, String name, String department, boolean active) {
    }

    private final UserRepo employees;
    private final ApprovalHierarchyRepo assignments;

    public StaffService(UserRepo employees, ApprovalHierarchyRepo assignments) {
        this.employees = employees;
        this.assignments = assignments;
    }

    public StaffCard card(Long employeeId) {
        User employee = employees.findById(employeeId).orElseThrow(NotFoundException::new);
        Optional<ApprovalHierarchy> assignment = assignments.findById(employeeId);
        Long approverId = assignment.map(ApprovalHierarchy::getManagerId).orElse(null);
        String approverName = approverId == null ? null
                : employees.findById(approverId).map(User::getName).orElse(null);
        return new StaffCard(employee.getUserId(), employee.getStaffId(), employee.getName(), employee.getEmail(),
                employee.getDepartment(), employee.getDesignation(), employee.isActive(), approverId, approverName);
    }

    public Optional<Long> approverOf(Long employeeId) {
        return assignments.findById(employeeId).map(ApprovalHierarchy::getManagerId);
    }

    public String nameOf(Long employeeId) {
        return employeeId == null ? null
                : employees.findById(employeeId).map(User::getName).orElse("Unknown");
    }

    public Map<Long, String> namesOf(Collection<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return names;
        }
        for (User employee : employees.findByIdIn(ids)) {
            names.put(employee.getUserId(), employee.getName());
        }
        return names;
    }

    public List<PersonRef> directReports(Long managerId) {
        return employees.findDirectReports(managerId).stream()
                .map(e -> new PersonRef(e.getId(), e.getName(), e.getDepartment(), e.isActive()))
                .toList();
    }
}
