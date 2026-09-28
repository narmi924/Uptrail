package com.uptrail.organisation.repository;

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

import com.uptrail.organisation.domain.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    /** Lock anchor of the write protocol: serialises all training writes of one employee. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Employee e where e.id = :id")
    Optional<Employee> lockById(@Param("id") Long id);

    boolean existsByStaffNo(String staffNo);

    long countByActive(boolean active);

    List<Employee> findByIdIn(Collection<Long> ids);

    @Query("""
            select e from Employee e
            where (:active is null or e.active = :active)
              and (:query = '' or lower(e.fullName) like concat('%', :query, '%')
                   or lower(e.staffNo) like concat('%', :query, '%')
                   or lower(e.department) like concat('%', :query, '%'))
            order by e.fullName, e.id
            """)
    Page<Employee> search(@Param("query") String query, @Param("active") Boolean active, Pageable pageable);

    @Query("""
            select e from Employee e, ApprovalAssignment a
            where a.employeeId = e.id and a.managerId = :managerId
            order by e.fullName, e.id
            """)
    List<Employee> findDirectReports(@Param("managerId") Long managerId);

    /** Active staff who can apply for courses but have no approver, so their submissions are blocked. */
    @Query("""
            select count(e) from Employee e, UserAccount u join u.roles r
            where u.employeeId = e.id and e.active = true and r = com.uptrail.identity.domain.Role.EMPLOYEE
              and not exists (select 1 from ApprovalAssignment a where a.employeeId = e.id)
            """)
    long countActiveApplicantsWithoutApprover();

    /** Active staff who can apply for courses but have no training account for the year. */
    @Query("""
            select count(e) from Employee e, UserAccount u join u.roles r
            where u.employeeId = e.id and e.active = true and r = com.uptrail.identity.domain.Role.EMPLOYEE
              and not exists (select 1 from TrainingAccount t where t.employeeId = e.id and t.calendarYear = :year)
            """)
    long countActiveApplicantsWithoutAccount(@Param("year") int year);
}
