package com.uptrail.application.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uptrail.application.domain.ApplicationStatus;
import com.uptrail.application.domain.CourseApplication;
import com.uptrail.catalogue.domain.CategoryCode;

public interface CourseApplicationRepository extends JpaRepository<CourseApplication, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CourseApplication a where a.id = :id")
    Optional<CourseApplication> lockById(@Param("id") Long id);

    @Query("select a.employeeId from CourseApplication a where a.id = :id")
    Optional<Long> findEmployeeIdById(@Param("id") Long id);

    Optional<CourseApplication> findByEmployeeIdAndClientRequestId(Long employeeId, String clientRequestId);

    /** Own applications whose inclusive date range overlaps the candidate period. */
    @Query("""
            select a from CourseApplication a
            where a.employeeId = :employeeId and a.status in :statuses
              and a.startDate <= :end and a.endDate >= :start
              and (:excludeId is null or a.id <> :excludeId)
            order by a.startDate, a.id
            """)
    List<CourseApplication> findOverlapping(@Param("employeeId") Long employeeId,
            @Param("statuses") Collection<ApplicationStatus> statuses, @Param("start") LocalDate start,
            @Param("end") LocalDate end, @Param("excludeId") Long excludeId);

    /**
     * Applications of one employee with training days in [from, to]. Start and end dates are always counted
     * working days, so a period overlapping the range always has training days inside it.
     */
    @Query("""
            select a from CourseApplication a
            where a.employeeId = :employeeId and a.startDate <= :to and a.endDate >= :from
              and (:status is null or a.status = :status)
              and (:category is null or a.category = :category)
              and (:query = '' or lower(a.courseTitle) like concat('%', :query, '%')
                   or lower(a.referenceNo) like concat('%', :query, '%'))
            order by a.startDate desc, a.id desc
            """)
    Page<CourseApplication> findForEmployeeInPeriod(@Param("employeeId") Long employeeId,
            @Param("from") LocalDate from, @Param("to") LocalDate to, @Param("status") ApplicationStatus status,
            @Param("category") CategoryCode category, @Param("query") String query, Pageable pageable);

    long countByEmployeeIdAndStatusIn(Long employeeId, Collection<ApplicationStatus> statuses);

    @Query("""
            select a from CourseApplication a
            where a.employeeId = :employeeId and a.status = com.uptrail.application.domain.ApplicationStatus.APPROVED
              and a.endDate < :today
            order by a.endDate
            """)
    List<CourseApplication> findReadyToComplete(@Param("employeeId") Long employeeId,
            @Param("today") LocalDate today);

    List<CourseApplication> findTop5ByEmployeeIdOrderByUpdatedAtDescIdDesc(Long employeeId);

    /** Units of COMPLETED applications in [from, to] per employee (completed usage statistic). */
    @Query("""
            select coalesce(sum(d.units), 0) from CourseApplication a join a.days d
            where a.employeeId = :employeeId and a.status = com.uptrail.application.domain.ApplicationStatus.COMPLETED
              and d.trainingDate between :from and :to
            """)
    Number sumCompletedUnits(@Param("employeeId") Long employeeId, @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    List<CourseApplication> findByApproverIdAndStatusInOrderByStartDateAscIdAsc(Long approverId,
            Collection<ApplicationStatus> statuses);

    long countByApproverIdAndStatusIn(Long approverId, Collection<ApplicationStatus> statuses);

    /** Approved (or completed) courses of the given people that overlap the period. */
    @Query("""
            select a from CourseApplication a
            where a.employeeId in :employeeIds and a.status in :statuses
              and a.startDate <= :end and a.endDate >= :start
            order by a.startDate, a.id
            """)
    List<CourseApplication> findForPeopleInPeriod(@Param("employeeIds") Collection<Long> employeeIds,
            @Param("statuses") Collection<ApplicationStatus> statuses, @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    List<CourseApplication> findByApproverIdAndStatusIn(Long approverId, Collection<ApplicationStatus> statuses);

    long countByEmployeeIdOrApproverIdOrReviewedBy(Long employeeId, Long approverId, Long reviewedBy);

    List<CourseApplication> findByEmployeeIdAndStatusIn(Long employeeId, Collection<ApplicationStatus> statuses);

    /** Pending or approved-but-not-completed applications whose period contains the date. */
    @Query("""
            select a from CourseApplication a
            where a.status in :statuses and a.startDate <= :date and a.endDate >= :date
            order by a.startDate, a.id
            """)
    List<CourseApplication> findActiveOn(@Param("date") LocalDate date,
            @Param("statuses") Collection<ApplicationStatus> statuses);

    /** Applications in the given statuses overlapping [from, to], ordered for calendar display. */
    @Query("""
            select a from CourseApplication a
            where a.status in :statuses and a.startDate <= :to and a.endDate >= :from
            order by a.startDate, a.endDate, a.id
            """)
    List<CourseApplication> findShownInPeriod(@Param("statuses") Collection<ApplicationStatus> statuses,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Completed fee-paying courses of the employee that do not have a fee claim yet. */
    @Query("""
            select a from CourseApplication a
            where a.employeeId = :employeeId
              and a.status = com.uptrail.application.domain.ApplicationStatus.COMPLETED
              and a.category in :categories and a.courseFee > 0
              and not exists (select c.id from CourseClaim c where c.applicationId = a.id)
            order by a.endDate desc, a.id desc
            """)
    List<CourseApplication> findClaimable(@Param("employeeId") Long employeeId,
            @Param("categories") Collection<CategoryCode> categories);
}
