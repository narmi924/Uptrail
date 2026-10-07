package com.uptrail.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uptrail.model.CourseCategory;

public interface CourseCategoryRepo extends JpaRepository<CourseCategory, com.uptrail.model.CategoryCode> {
}
