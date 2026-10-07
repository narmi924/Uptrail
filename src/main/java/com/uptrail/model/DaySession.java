package com.uptrail.model;

/**
 * Part of a working day consumed by training: one half-day unit (AM or PM) or the whole day (BOTH, two units).
 */
public enum DaySession {

    AM(1),
    PM(1),
    BOTH(2);

    private final int units;

    DaySession(int units) {
        this.units = units;
    }

    public int units() {
        return units;
    }
}
