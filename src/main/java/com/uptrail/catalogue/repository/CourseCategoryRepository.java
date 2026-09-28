package com.uptrail.catalogue.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uptrail.catalogue.domain.CourseCategory;

public interface CourseCategoryRepository extends JpaRepository<CourseCategory, com.uptrail.catalogue.domain.CategoryCode> {
}
