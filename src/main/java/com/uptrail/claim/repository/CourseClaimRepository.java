package com.uptrail.claim.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uptrail.claim.domain.ClaimStatus;
import com.uptrail.claim.domain.CourseClaim;

public interface CourseClaimRepository extends JpaRepository<CourseClaim, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CourseClaim c where c.id = :id")
    Optional<CourseClaim> lockById(@Param("id") Long id);

    Optional<CourseClaim> findByApplicationId(Long applicationId);

    boolean existsByApplicationId(Long applicationId);

    /** The owner of a claim, read before locking: the application's employee never changes. */
    @Query("select a.employeeId from CourseClaim c, CourseApplication a where a.id = c.applicationId and c.id = :id")
    Optional<Long> findEmployeeIdById(@Param("id") Long id);

    @Query(value = """
            select c from CourseClaim c, CourseApplication a
            where a.id = c.applicationId and a.employeeId = :employeeId
            order by c.submittedAt desc, c.id desc
            """, countQuery = """
            select count(c) from CourseClaim c, CourseApplication a
            where a.id = c.applicationId and a.employeeId = :employeeId
            """)
    Page<CourseClaim> findOwn(@Param("employeeId") Long employeeId, Pageable pageable);

    Page<CourseClaim> findByApproverIdAndStatusOrderBySubmittedAtAscIdAsc(Long approverId, ClaimStatus status,
            Pageable pageable);

    Page<CourseClaim> findByStatusOrderByReviewedAtAscIdAsc(ClaimStatus status, Pageable pageable);

    Page<CourseClaim> findByStatusOrderByReimbursedAtDescIdDesc(ClaimStatus status, Pageable pageable);

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
