package com.uptrail.catalogue.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uptrail.catalogue.domain.CatalogueCourse;

public interface CatalogueCourseRepository extends JpaRepository<CatalogueCourse, Long> {
}
