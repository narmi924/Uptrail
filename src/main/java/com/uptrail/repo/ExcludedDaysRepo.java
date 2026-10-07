package com.uptrail.repo;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uptrail.model.ExcludedDays;

public interface ExcludedDaysRepo extends JpaRepository<ExcludedDays, LocalDate> {

    List<ExcludedDays> findByHolidayDateBetweenOrderByHolidayDate(LocalDate from, LocalDate to);

    long countByHolidayDateBetween(LocalDate from, LocalDate to);
}
