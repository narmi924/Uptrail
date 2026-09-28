package com.uptrail.organisation.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uptrail.organisation.domain.ApprovalAssignment;
import com.uptrail.organisation.domain.Designation;
import com.uptrail.organisation.domain.Employee;
import com.uptrail.organisation.repository.ApprovalAssignmentRepository;
import com.uptrail.organisation.repository.EmployeeRepository;
import com.uptrail.shared.error.NotFoundException;

/**
 * Read access to people and their current approver.
 */
@Service
@Transactional(readOnly = true)
public class EmployeeDirectoryService {

    public record EmployeeCard(Long id, String staffNo, String fullName, String email, String department,
            Designation designation, boolean active, Long approverId, String approverName) {
    }

    public record PersonRef(Long id, String fullName, String department, boolean active) {
    }

    private final EmployeeRepository employees;
    private final ApprovalAssignmentRepository assignments;

    public EmployeeDirectoryService(EmployeeRepository employees, ApprovalAssignmentRepository assignments) {
        this.employees = employees;
        this.assignments = assignments;
    }

    public EmployeeCard card(Long employeeId) {
        Employee employee = employees.findById(employeeId).orElseThrow(NotFoundException::new);
        Optional<ApprovalAssignment> assignment = assignments.findById(employeeId);
        Long approverId = assignment.map(ApprovalAssignment::getManagerId).orElse(null);
        String approverName = approverId == null ? null
                : employees.findById(approverId).map(Employee::getFullName).orElse(null);
        return new EmployeeCard(employee.getId(), employee.getStaffNo(), employee.getFullName(), employee.getEmail(),
                employee.getDepartment(), employee.getDesignation(), employee.isActive(), approverId, approverName);
    }

    public Optional<Long> approverOf(Long employeeId) {
        return assignments.findById(employeeId).map(ApprovalAssignment::getManagerId);
    }

    public String nameOf(Long employeeId) {
        return employeeId == null ? null
                : employees.findById(employeeId).map(Employee::getFullName).orElse("Unknown");
    }

    public Map<Long, String> namesOf(Collection<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return names;
        }
        for (Employee employee : employees.findByIdIn(ids)) {
            names.put(employee.getId(), employee.getFullName());
        }
        return names;
    }

    public List<PersonRef> directReports(Long managerId) {
        return employees.findDirectReports(managerId).stream()
                .map(e -> new PersonRef(e.getId(), e.getFullName(), e.getDepartment(), e.isActive()))
                .toList();
    }
}
