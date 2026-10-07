package com.uptrail.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uptrail.model.TrainingProvider;

public interface TrainingProviderRepo extends JpaRepository<TrainingProvider, Long> {
}
