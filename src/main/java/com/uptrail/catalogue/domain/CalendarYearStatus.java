package com.uptrail.catalogue.domain;

public enum CalendarYearStatus {

    /** Holidays are being prepared; applications touching this year cannot be submitted or approved. */
    DRAFT,

    /** An administrator confirmed the holiday list; working days can be computed for this year. */
    CONFIRMED
}
