package com.uptrail.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uptrail.model.ApprovalHierarchy;

public interface ApprovalHierarchyRepo extends JpaRepository<ApprovalHierarchy, Long> {

    List<ApprovalHierarchy> findByManagerId(Long managerId);

    boolean existsByEmployeeIdAndManagerId(Long employeeId, Long managerId);

    long countByManagerId(Long managerId);
}
