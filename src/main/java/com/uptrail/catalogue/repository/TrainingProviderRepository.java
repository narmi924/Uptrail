package com.uptrail.catalogue.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uptrail.catalogue.domain.TrainingProvider;

public interface TrainingProviderRepository extends JpaRepository<TrainingProvider, Long> {
}
