package com.uptrail.entitlement.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uptrail.entitlement.domain.LedgerEntry;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
}
