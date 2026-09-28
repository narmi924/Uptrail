package com.uptrail.catalogue.domain;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Preparation state of one year's public holiday calendar. A missing or unconfirmed year never means
 * "no holidays": training days for that year are not computed until an administrator confirms it.
 */
@Entity
@Table(name = "training_calendar_year")
public class TrainingCalendarYear {

    @Id
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "calendar_year", nullable = false)
    private Integer calendarYear;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 16)
    private CalendarYearStatus status;

    @Column(name = "source_note", nullable = false, length = 400)
    private String sourceNote;

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "confirmed_holiday_count")
    private Integer confirmedHolidayCount;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TrainingCalendarYear() {
    }

    public static TrainingCalendarYear draft(int year, String sourceNote, Instant now) {
        TrainingCalendarYear calendarYear = new TrainingCalendarYear();
        calendarYear.calendarYear = year;
        calendarYear.status = CalendarYearStatus.DRAFT;
        calendarYear.sourceNote = Objects.requireNonNull(sourceNote);
        calendarYear.createdAt = now;
        calendarYear.updatedAt = now;
        return calendarYear;
    }

    public void confirm(Long adminEmployeeId, int holidayCount, String sourceNote, Instant now) {
        if (holidayCount < 1) {
            throw new IllegalStateException("A calendar year needs at least one holiday before confirmation");
        }
        this.status = CalendarYearStatus.CONFIRMED;
        this.sourceNote = Objects.requireNonNull(sourceNote);
        this.confirmedBy = adminEmployeeId;
        this.confirmedAt = now;
        this.confirmedHolidayCount = holidayCount;
        this.updatedAt = now;
    }

    /** A change to the set of holiday dates invalidates the previous confirmation. */
    public void reopen(Instant now) {
        this.status = CalendarYearStatus.DRAFT;
        this.confirmedBy = null;
        this.confirmedAt = null;
        this.confirmedHolidayCount = null;
        this.updatedAt = now;
    }

    public void updateSourceNote(String sourceNote, Instant now) {
        this.sourceNote = Objects.requireNonNull(sourceNote);
        this.updatedAt = now;
    }

    public boolean isConfirmed() {
        return status == CalendarYearStatus.CONFIRMED;
    }

    public Integer getCalendarYear() {
        return calendarYear;
    }

    public CalendarYearStatus getStatus() {
        return status;
    }

    public String getSourceNote() {
        return sourceNote;
    }

    public Long getConfirmedBy() {
        return confirmedBy;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Integer getConfirmedHolidayCount() {
        return confirmedHolidayCount;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
