package com.uptrail.model;

public enum ClaimStatus {

    SUBMITTED("Submitted"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    /** Registered by an administrator; Uptrail records the reimbursement but does not transfer money. */
    REIMBURSED("Reimbursed");

    private final String label;

    ClaimStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
