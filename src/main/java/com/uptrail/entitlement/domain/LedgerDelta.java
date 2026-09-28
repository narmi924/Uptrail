package com.uptrail.entitlement.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Change of the reserved, committed and reimbursed figures of one annual account.
 */
public record LedgerDelta(int reservedUnits, int committedUnits, BigDecimal reservedAmount,
        BigDecimal committedAmount, BigDecimal reimbursedAmount) {

    public static final LedgerDelta ZERO = new LedgerDelta(0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    public LedgerDelta {
        reservedAmount = money(reservedAmount);
        committedAmount = money(committedAmount);
        reimbursedAmount = money(reimbursedAmount);
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.UNNECESSARY);
    }

    public static LedgerDelta reserve(int units, BigDecimal amount) {
        return new LedgerDelta(units, 0, amount, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public static LedgerDelta reimburse(BigDecimal amount) {
        return new LedgerDelta(0, 0, BigDecimal.ZERO, BigDecimal.ZERO, amount);
    }

    public LedgerDelta plus(LedgerDelta other) {
        return new LedgerDelta(reservedUnits + other.reservedUnits, committedUnits + other.committedUnits,
                reservedAmount.add(other.reservedAmount), committedAmount.add(other.committedAmount),
                reimbursedAmount.add(other.reimbursedAmount));
    }

    public LedgerDelta negate() {
        return new LedgerDelta(-reservedUnits, -committedUnits, reservedAmount.negate(), committedAmount.negate(),
                reimbursedAmount.negate());
    }

    /** Moves everything currently reserved into the committed columns. */
    public LedgerDelta reservedToCommitted() {
        return new LedgerDelta(-reservedUnits, reservedUnits, reservedAmount.negate(), reservedAmount,
                BigDecimal.ZERO);
    }

    public boolean isZero() {
        return reservedUnits == 0 && committedUnits == 0 && reservedAmount.signum() == 0
                && committedAmount.signum() == 0 && reimbursedAmount.signum() == 0;
    }
}
