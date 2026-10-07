package com.uptrail.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One immutable net change of an annual account caused by one business event. Corrections are new
 * entries; existing entries are never edited.
 */
@Entity
@Immutable
@Table(name = "training_ledger")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "claim_id")
    private Long claimId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "entry_type", nullable = false, length = 40)
    private LedgerEntryType entryType;

    @Column(name = "reserved_units_delta", nullable = false)
    private int reservedUnitsDelta;

    @Column(name = "committed_units_delta", nullable = false)
    private int committedUnitsDelta;

    @Column(name = "reserved_amount_delta", nullable = false, precision = 12, scale = 2)
    private BigDecimal reservedAmountDelta;

    @Column(name = "committed_amount_delta", nullable = false, precision = 12, scale = 2)
    private BigDecimal committedAmountDelta;

    @Column(name = "reimbursed_amount_delta", nullable = false, precision = 12, scale = 2)
    private BigDecimal reimbursedAmountDelta;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    public static LedgerEntry of(Long accountId, Long eventId, Long applicationId, Long claimId,
            LedgerEntryType type, LedgerDelta delta, Instant now) {
        LedgerEntry entry = new LedgerEntry();
        entry.accountId = Objects.requireNonNull(accountId);
        entry.eventId = Objects.requireNonNull(eventId);
        entry.applicationId = applicationId;
        entry.claimId = claimId;
        entry.entryType = Objects.requireNonNull(type);
        entry.reservedUnitsDelta = delta.reservedUnits();
        entry.committedUnitsDelta = delta.committedUnits();
        entry.reservedAmountDelta = delta.reservedAmount();
        entry.committedAmountDelta = delta.committedAmount();
        entry.reimbursedAmountDelta = delta.reimbursedAmount();
        entry.createdAt = now;
        return entry;
    }

    public Long getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public Long getEventId() {
        return eventId;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public Long getClaimId() {
        return claimId;
    }

    public LedgerEntryType getEntryType() {
        return entryType;
    }

    public int getReservedUnitsDelta() {
        return reservedUnitsDelta;
    }

    public int getCommittedUnitsDelta() {
        return committedUnitsDelta;
    }

    public BigDecimal getReservedAmountDelta() {
        return reservedAmountDelta;
    }

    public BigDecimal getCommittedAmountDelta() {
        return committedAmountDelta;
    }

    public BigDecimal getReimbursedAmountDelta() {
        return reimbursedAmountDelta;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
