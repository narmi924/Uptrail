package com.uptrail.application.domain;

import java.time.LocalDate;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.uptrail.entitlement.domain.DaySession;

/**
 * One working day that counts towards the training entitlement, as calculated when the application was
 * submitted or last updated. Later holiday changes do not rewrite this snapshot.
 */
@Embeddable
public class ScheduledDay {

    @Column(name = "training_date", nullable = false)
    private LocalDate trainingDate;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "units", nullable = false)
    private int units;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "session_code", nullable = false, length = 4)
    private DaySession session;

    protected ScheduledDay() {
    }

    public ScheduledDay(LocalDate trainingDate, DaySession session) {
        this.trainingDate = Objects.requireNonNull(trainingDate);
        this.session = Objects.requireNonNull(session);
        this.units = session.units();
    }

    public LocalDate getTrainingDate() {
        return trainingDate;
    }

    public int getUnits() {
        return units;
    }

    public DaySession getSession() {
        return session;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ScheduledDay day && trainingDate.equals(day.trainingDate) && session == day.session;
    }

    @Override
    public int hashCode() {
        return Objects.hash(trainingDate, session);
    }
}
