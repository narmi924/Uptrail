package com.uptrail.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Annual entitlement configuration of one employee. Balances are never stored here: they are the sum of
 * the account's ledger entries.
 */
@Entity
@Table(name = "training_entitlement")
public class TrainingEntitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "calendar_year", nullable = false)
    private Integer calendarYear;

    /** Training days in half-day units: 2 units = 1 day. */
    @Column(name = "entitled_units", nullable = false)
    private int entitledUnits;

    @Column(name = "budget_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal budgetAmount;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TrainingEntitlement() {
    }

    public static TrainingEntitlement open(Long employeeId, int calendarYear, int entitledUnits, BigDecimal budgetAmount,
            Instant now) {
        TrainingEntitlement account = new TrainingEntitlement();
        account.employeeId = Objects.requireNonNull(employeeId);
        account.calendarYear = calendarYear;
        account.reconfigure(entitledUnits, budgetAmount, now);
        return account;
    }

    public void reconfigure(int entitledUnits, BigDecimal budgetAmount, Instant now) {
        if (entitledUnits < 0 || budgetAmount == null || budgetAmount.signum() < 0) {
            throw new IllegalArgumentException("Entitlement and budget cannot be negative");
        }
        this.entitledUnits = entitledUnits;
        this.budgetAmount = budgetAmount;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public int getCalendarYear() {
        return calendarYear;
    }

    public int getEntitledUnits() {
        return entitledUnits;
    }

    public BigDecimal getBudgetAmount() {
        return budgetAmount;
    }

    public long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
