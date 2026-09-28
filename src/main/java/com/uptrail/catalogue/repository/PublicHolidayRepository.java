package com.uptrail.catalogue.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uptrail.catalogue.domain.PublicHoliday;

public interface PublicHolidayRepository extends JpaRepository<PublicHoliday, java.time.LocalDate> {
}
