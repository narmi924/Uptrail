package com.uptrail.claim.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uptrail.claim.domain.ClaimStatus;
import com.uptrail.claim.domain.CourseClaim;

public interface CourseClaimRepository extends JpaRepository<CourseClaim, Long> {

    long countByStatus(ClaimStatus status);

    long countByApproverIdAndStatus(Long approverId, ClaimStatus status);

    List<CourseClaim> findByApproverIdAndStatus(Long approverId, ClaimStatus status);

    long countByReviewedByOrReimbursedBy(Long reviewedBy, Long reimbursedBy);

    /** Claims of one employee's applications in the given status (used to reassign submitted claims). */
    @Query("""
            select c from CourseClaim c, CourseApplication a
            where a.id = c.applicationId and a.employeeId = :employeeId and c.status = :status
            order by c.id
            """)
    List<CourseClaim> findForEmployee(@Param("employeeId") Long employeeId, @Param("status") ClaimStatus status);

    interface ClaimTotal {
        Long getEmployeeId();

        ClaimStatus getStatus();

        java.math.BigDecimal getAmount();
    }

    /** Claim amounts per employee and status for applications starting in [from, to]. */
    @Query("""
            select a.employeeId as employeeId, c.status as status, sum(c.amount) as amount
            from CourseClaim c, CourseApplication a
            where a.id = c.applicationId and a.employeeId in :employeeIds and a.startDate between :from and :to
            group by a.employeeId, c.status
            """)
    List<ClaimTotal> totalsByEmployee(@Param("employeeIds") java.util.Collection<Long> employeeIds,
            @Param("from") java.time.LocalDate from, @Param("to") java.time.LocalDate to);
}
