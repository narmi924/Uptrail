package com.uptrail.entitlement.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uptrail.entitlement.domain.TrainingAccount;

public interface TrainingAccountRepository extends JpaRepository<TrainingAccount, Long> {

    Optional<TrainingAccount> findByEmployeeIdAndCalendarYear(Long employeeId, int calendarYear);

    List<TrainingAccount> findByCalendarYear(int calendarYear);

    List<TrainingAccount> findByEmployeeIdInAndCalendarYear(Collection<Long> employeeIds, int calendarYear);

    boolean existsByEmployeeId(Long employeeId);

    List<TrainingAccount> findByEmployeeId(Long employeeId);

    /**
     * Locks the accounts of the given years. One statement ordered by year: InnoDB takes the row locks in
     * index order (employee, year), which keeps the lock order consistent across all writers.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from TrainingAccount a
            where a.employeeId = :employeeId and a.calendarYear in :years
            order by a.calendarYear
            """)
    List<TrainingAccount> lockForYears(@Param("employeeId") Long employeeId, @Param("years") Collection<Integer> years);
}
