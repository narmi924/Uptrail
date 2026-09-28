package com.uptrail.claim.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uptrail.claim.domain.ClaimStatus;
import com.uptrail.claim.domain.CourseClaim;

public interface CourseClaimRepository extends JpaRepository<CourseClaim, Long> {

    long countByStatus(ClaimStatus status);
}
