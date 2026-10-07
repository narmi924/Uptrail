package com.uptrail.repo;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uptrail.model.CatalogueCourse;
import com.uptrail.model.CategoryCode;

public interface CatalogueCourseRepo extends JpaRepository<CatalogueCourse, Long> {

    @Query("""
            select c from CatalogueCourse c
            where c.active = true
              and (:category is null or c.category = :category)
              and (:query = '' or lower(c.title) like concat('%', :query, '%'))
            order by c.title, c.id
            """)
    List<CatalogueCourse> searchActive(@Param("query") String query, @Param("category") CategoryCode category,
            Pageable pageable);

    List<CatalogueCourse> findAllByOrderByCategoryAscTitleAsc();

    boolean existsByProviderId(Long providerId);
}
