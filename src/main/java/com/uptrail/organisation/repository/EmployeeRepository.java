package com.uptrail.organisation.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uptrail.organisation.domain.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
}
