package com.uptrail.claim.domain;

public enum DocumentType {

    RECEIPT("Receipt"),
    COMPLETION_CERTIFICATE("Certificate of completion");

    private final String label;

    DocumentType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
