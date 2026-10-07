package com.uptrail.repo;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uptrail.model.ClaimStatus;
import com.uptrail.model.CourseFeeApplication;

public interface CourseFeeApplicationRepo extends JpaRepository<CourseFeeApplication, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CourseFeeApplication c where c.id = :id")
    Optional<CourseFeeApplication> lockById(@Param("id") Long id);

    Optional<CourseFeeApplication> findByApplicationId(Long applicationId);

    boolean existsByApplicationId(Long applicationId);

    /** The owner of a claim, read before locking: the application's employee never changes. */
    @Query("select a.applicantId from CourseFeeApplication c, CourseApplication a where a.id = c.applicationId and c.id = :id")
    Optional<Long> findEmployeeIdById(@Param("id") Long id);

    @Query(value = """
            select c from CourseFeeApplication c, CourseApplication a
            where a.id = c.applicationId and a.applicantId = :employeeId
            order by c.submittedAt desc, c.id desc
            """, countQuery = """
            select count(c) from CourseFeeApplication c, CourseApplication a
            where a.id = c.applicationId and a.applicantId = :employeeId
            """)
    Page<CourseFeeApplication> findOwn(@Param("employeeId") Long employeeId, Pageable pageable);

    Page<CourseFeeApplication> findByApproverIdAndStatusOrderBySubmittedAtAscIdAsc(Long approverId, ClaimStatus status,
            Pageable pageable);

    Page<CourseFeeApplication> findByStatusOrderByReviewedAtAscIdAsc(ClaimStatus status, Pageable pageable);

    Page<CourseFeeApplication> findByStatusOrderByReimbursedAtDescIdDesc(ClaimStatus status, Pageable pageable);

    long countByStatus(ClaimStatus status);

    long countByApproverIdAndStatus(Long approverId, ClaimStatus status);

    List<CourseFeeApplication> findByApproverIdAndStatus(Long approverId, ClaimStatus status);

    long countByReviewedByOrReimbursedBy(Long reviewedBy, Long reimbursedBy);

    /** Claims of one employee's applications in the given status (used to reassign submitted claims). */
    @Query("""
            select c from CourseFeeApplication c, CourseApplication a
            where a.id = c.applicationId and a.applicantId = :employeeId and c.status = :status
            order by c.id
            """)
    List<CourseFeeApplication> findForEmployee(@Param("employeeId") Long employeeId, @Param("status") ClaimStatus status);

    interface ClaimTotal {
        Long getEmployeeId();

        ClaimStatus getStatus();

        java.math.BigDecimal getAmount();
    }

    /** Claim amounts per employee and status for applications starting in [from, to]. */
    @Query("""
            select a.applicantId as employeeId, c.status as status, sum(c.amount) as amount
            from CourseFeeApplication c, CourseApplication a
            where a.id = c.applicationId and a.applicantId in :employeeIds and a.startDate between :from and :to
            group by a.applicantId, c.status
            """)
    List<ClaimTotal> totalsByEmployee(@Param("employeeIds") java.util.Collection<Long> employeeIds,
            @Param("from") java.time.LocalDate from, @Param("to") java.time.LocalDate to);
}
