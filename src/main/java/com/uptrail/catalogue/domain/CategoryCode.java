package com.uptrail.catalogue.domain;

/**
 * The three course categories required by the course brief. Fee and half-day rules are fixed here and
 * cannot be changed from the administration pages.
 */
public enum CategoryCode {

    INTERNAL("Internal Training", false, true),
    EXTERNAL("External Course", true, false),
    CERTIFICATION("Professional Certification", true, false);

    private final String defaultLabel;
    private final boolean feePaying;
    private final boolean halfDayAllowed;

    CategoryCode(String defaultLabel, boolean feePaying, boolean halfDayAllowed) {
        this.defaultLabel = defaultLabel;
        this.feePaying = feePaying;
        this.halfDayAllowed = halfDayAllowed;
    }

    public String defaultLabel() {
        return defaultLabel;
    }

    /** External courses and certifications are charged against the annual training budget. */
    public boolean isFeePaying() {
        return feePaying;
    }

    /** Only internal training may start or end on a half day. */
    public boolean isHalfDayAllowed() {
        return halfDayAllowed;
    }
}
