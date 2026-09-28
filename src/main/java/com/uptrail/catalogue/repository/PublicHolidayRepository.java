package com.uptrail.catalogue.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uptrail.catalogue.domain.PublicHoliday;

public interface PublicHolidayRepository extends JpaRepository<PublicHoliday, LocalDate> {

    List<PublicHoliday> findByHolidayDateBetweenOrderByHolidayDate(LocalDate from, LocalDate to);

    long countByHolidayDateBetween(LocalDate from, LocalDate to);
}
