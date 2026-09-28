package com.uptrail.identity.domain;

/**
 * Security roles. A manager account also holds EMPLOYEE so that managers can apply for courses.
 */
public enum Role {

    EMPLOYEE("Employee"),
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
