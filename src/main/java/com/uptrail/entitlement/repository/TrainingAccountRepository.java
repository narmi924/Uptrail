package com.uptrail.entitlement.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uptrail.entitlement.domain.TrainingAccount;

public interface TrainingAccountRepository extends JpaRepository<TrainingAccount, Long> {
}
