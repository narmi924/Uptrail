package com.uptrail.catalogue.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A Singapore public holiday. The database derives the calendar year from the date and requires a
 * matching {@code training_calendar_year} row.
 */
@Entity
@Table(name = "public_holiday")
public class PublicHoliday {

    @Id
    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "source_note", nullable = false, length = 400)
    private String sourceNote;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PublicHoliday() {
    }

    public static PublicHoliday create(LocalDate date, String name, String sourceNote, Instant now) {
        PublicHoliday holiday = new PublicHoliday();
        holiday.holidayDate = Objects.requireNonNull(date);
        holiday.name = Objects.requireNonNull(name);
        holiday.sourceNote = Objects.requireNonNull(sourceNote);
        holiday.updatedAt = now;
        return holiday;
    }

    public void describe(String name, String sourceNote, Instant now) {
        this.name = Objects.requireNonNull(name);
        this.sourceNote = Objects.requireNonNull(sourceNote);
        this.updatedAt = now;
    }

    public LocalDate getHolidayDate() {
        return holidayDate;
    }

    public String getName() {
        return name;
    }

    public String getSourceNote() {
        return sourceNote;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
