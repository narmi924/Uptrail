package com.uptrail.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.uptrail.shared.time.BusinessClock;

/**
 * Test-only clock that tests can move, e.g. past a course end date before marking it completed.
 */
public class MutableClock extends Clock {

    private volatile Instant instant;

    public MutableClock(Instant instant) {
        this.instant = instant;
    }

    public static Instant at(LocalDate date, int hour) {
        return ZonedDateTime.of(date, LocalTime.of(hour, 0), BusinessClock.ZONE).toInstant();
    }

    public void setDate(LocalDate date) {
        this.instant = at(date, 9);
    }

    public void set(Instant instant) {
        this.instant = instant;
    }

    public void advance(Duration duration) {
        this.instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return BusinessClock.ZONE;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
