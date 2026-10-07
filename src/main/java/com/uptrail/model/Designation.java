package com.uptrail.model;

/**
 * Job designation. It drives the default annual entitlement offered to administrators when they create a
 * training account; it is independent of the security roles (an administrative employee is not an ADMIN).
 */
public enum Designation {

    ADMINISTRATIVE("Administrative"),
    PROFESSIONAL("Professional"),
    MANAGEMENT("Management");

    private final String label;

    Designation(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
