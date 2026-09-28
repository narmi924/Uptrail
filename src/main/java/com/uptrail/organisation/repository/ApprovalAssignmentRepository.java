package com.uptrail.organisation.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uptrail.organisation.domain.ApprovalAssignment;

public interface ApprovalAssignmentRepository extends JpaRepository<ApprovalAssignment, Long> {
}
