package com.uptrail.model;

/**
 * Security roles. A manager account also holds STAFF so that managers can apply for courses.
 */
public enum Role {

    STAFF("Staff"),
    MANAGER("Manager"),
    ADMIN("Administrator");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String authority() {
        return "ROLE_" + name();
    }
}
