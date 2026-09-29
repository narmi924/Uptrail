package com.uptrail.entitlement.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uptrail.entitlement.domain.LedgerEntry;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    /** Column sums of a set of ledger entries; units and amounts are returned as numbers. */
    interface Totals {
        Number getReservedUnits();

        Number getCommittedUnits();

        Number getReservedAmount();

        Number getCommittedAmount();

        Number getReimbursedAmount();
    }

    @Query("""
            select coalesce(sum(l.reservedUnitsDelta), 0) as reservedUnits,
                   coalesce(sum(l.committedUnitsDelta), 0) as committedUnits,
                   coalesce(sum(l.reservedAmountDelta), 0) as reservedAmount,
                   coalesce(sum(l.committedAmountDelta), 0) as committedAmount,
                   coalesce(sum(l.reimbursedAmountDelta), 0) as reimbursedAmount
            from LedgerEntry l where l.accountId = :accountId
            """)
    Totals totalsForAccount(@Param("accountId") Long accountId);

    @Query("""
            select coalesce(sum(l.reservedUnitsDelta), 0) as reservedUnits,
                   coalesce(sum(l.committedUnitsDelta), 0) as committedUnits,
                   coalesce(sum(l.reservedAmountDelta), 0) as reservedAmount,
                   coalesce(sum(l.committedAmountDelta), 0) as committedAmount,
                   coalesce(sum(l.reimbursedAmountDelta), 0) as reimbursedAmount
            from LedgerEntry l where l.accountId = :accountId and l.applicationId = :applicationId
            """)
    Totals totalsForApplication(@Param("accountId") Long accountId, @Param("applicationId") Long applicationId);

    List<LedgerEntry> findByApplicationIdOrderByIdAsc(Long applicationId);

    List<LedgerEntry> findByAccountIdOrderByCreatedAtDescIdDesc(Long accountId);

    List<LedgerEntry> findByEventId(Long eventId);
}
