package com.uptrail.organisation.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uptrail.organisation.domain.ApprovalAssignment;

public interface ApprovalAssignmentRepository extends JpaRepository<ApprovalAssignment, Long> {

    List<ApprovalAssignment> findByManagerId(Long managerId);

    boolean existsByEmployeeIdAndManagerId(Long employeeId, Long managerId);

    long countByManagerId(Long managerId);
}
