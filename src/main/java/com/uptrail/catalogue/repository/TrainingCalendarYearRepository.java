package com.uptrail.catalogue.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uptrail.catalogue.domain.TrainingCalendarYear;

public interface TrainingCalendarYearRepository extends JpaRepository<TrainingCalendarYear, Integer> {

    /** Serialises holiday maintenance of one year. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select y from TrainingCalendarYear y where y.calendarYear = :year")
    java.util.Optional<TrainingCalendarYear> lockById(@org.springframework.data.repository.query.Param("year") Integer year);

    long countByConfirmedBy(Long confirmedBy);

    java.util.List<TrainingCalendarYear> findAllByOrderByCalendarYearAsc();
}
