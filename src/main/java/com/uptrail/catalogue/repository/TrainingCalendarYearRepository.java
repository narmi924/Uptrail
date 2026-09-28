package com.uptrail.catalogue.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uptrail.catalogue.domain.TrainingCalendarYear;

public interface TrainingCalendarYearRepository extends JpaRepository<TrainingCalendarYear, Integer> {
}
