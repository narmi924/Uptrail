package com.uptrail.application.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uptrail.application.domain.CourseApplication;

public interface CourseApplicationRepository extends JpaRepository<CourseApplication, Long> {
}
