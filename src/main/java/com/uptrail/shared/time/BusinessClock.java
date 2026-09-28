package com.uptrail.shared.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

/**
 * Single source of business time. All business dates (today, current year, "course has ended")
 * are evaluated in the Singapore time zone from the injected {@link Clock}.
 */
@Component
public class BusinessClock {

    public static final ZoneId ZONE = ZoneId.of("Asia/Singapore");

    private final Clock clock;
    private final ThreadLocal<Instant> scopedInstant = new ThreadLocal<>();

    public BusinessClock(Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        Instant scoped = scopedInstant.get();
        return scoped != null ? scoped : clock.instant();
    }

    public LocalDate today() {
        return LocalDate.ofInstant(now(), ZONE);
    }

    public int currentYear() {
        return today().getYear();
    }

    public YearMonth currentMonth() {
        return YearMonth.from(today());
    }

    /**
     * Runs an action as if the current instant were {@code instant}, on the calling thread only.
     * Used exclusively by the sample-data seeder at start-up to create historical records through the
     * normal services. It is deliberately not reachable from HTTP requests or configuration.
     */
    public <T> T callAt(Instant instant, Supplier<T> action) {
        Instant previous = scopedInstant.get();
        scopedInstant.set(instant);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                scopedInstant.remove();
            } else {
                scopedInstant.set(previous);
            }
        }
    }
}
